package org.gbif.occurrence.search.heatmap;

import org.gbif.api.model.occurrence.search.OccurrenceSearchParameter;
import org.gbif.api.util.SearchTypeValidator;
import org.gbif.api.util.VocabularyUtils;
import java.util.List;
import java.util.Map;
import javax.servlet.http.HttpServletRequest;

import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OccurrenceHeatmapRequestProvider {

  public static final String PARAM_QUERY_STRING = "q";
  public static final String GEOM_PARAM = "geom";
  public static final String ZOOM_PARAM = "z";
  private static final int DEFAULT_ZOOM_LEVEL = 3;
  private static final Logger LOG = LoggerFactory.getLogger(OccurrenceHeatmapRequestProvider.class);

  public static OccurrenceHeatmapRequest buildOccurrenceHeatmapRequest(HttpServletRequest request){
    OccurrenceHeatmapRequest occurrenceHeatmapSearchRequest = new OccurrenceHeatmapRequest();

    final String q = request.getParameter(PARAM_QUERY_STRING);

    if (!Strings.isNullOrEmpty(q)) {
      occurrenceHeatmapSearchRequest.setQ(q);
    }
    // find search parameter enum based filters
    setSearchParams(occurrenceHeatmapSearchRequest, request);
    return occurrenceHeatmapSearchRequest;
  }

  /**
   * Iterates over the params map and adds to the search request the recognized parameters (i.e.: those that have a
   * correspondent value in the P generic parameter).
   * Empty (of all size) and null parameters are discarded.
   */
  private static void setSearchParams(OccurrenceHeatmapRequest occurrenceHeatmapSearchRequest, HttpServletRequest request) {
    for (Map.Entry<String, String[]> entry : request.getParameterMap().entrySet()) {
      OccurrenceSearchParameter p = findSearchParam(entry.getKey());
      if (p != null) {
        for (String val : removeEmptyParameters(entry.getValue())) {
          SearchTypeValidator.validate(p, val);
          occurrenceHeatmapSearchRequest.addParameter(p, val);
        }
      }
    }

    occurrenceHeatmapSearchRequest.setZoom(getIntParam(request, ZOOM_PARAM, DEFAULT_ZOOM_LEVEL));
    if (request.getParameterMap().containsKey(GEOM_PARAM)) {
      occurrenceHeatmapSearchRequest.setGeometry(request.getParameterMap().get(GEOM_PARAM)[0]);
    }

    LOG.debug("Querying using Geometry", occurrenceHeatmapSearchRequest.getGeometry());

  }


  private static OccurrenceSearchParameter findSearchParam(String name) {
    try {
      return (OccurrenceSearchParameter) VocabularyUtils.lookupEnum(name, OccurrenceSearchParameter.class);
    } catch (IllegalArgumentException e) {
      // we have all params here, not only the enum ones, so this is ok to end up here a few times
    }
    return null;
  }


  /**
   * Removes all empty and null parameters from the list.
   * Each value is trimmed(String.trim()) in order to remove all sizes of empty parameters.
   */
  private static List<String> removeEmptyParameters(String[] parameters) {
    List<String> cleanParameters = Lists.newArrayListWithCapacity(parameters.length);
    for (String param : parameters) {
      final String cleanParam = Strings.nullToEmpty(param).trim();
      if (cleanParam.length() > 0) {
        cleanParameters.add(cleanParam);
      }
    }
    return cleanParameters;
  }

  public static int getIntParam(HttpServletRequest request, String param, int defaultVal) {
    final String[] vals = request.getParameterValues(param);
    if (vals != null && vals.length > 0) {
      try {
        return Integer.parseInt(vals[0]);
      } catch (NumberFormatException e) {
        return defaultVal;
      }
    }
    return defaultVal;
  }
}
