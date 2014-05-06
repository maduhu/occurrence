package org.gbif.occurrence.processor.interpreting;

import org.gbif.api.model.common.MediaObject;
import org.gbif.api.model.occurrence.Occurrence;
import org.gbif.api.model.occurrence.VerbatimOccurrence;
import org.gbif.api.vocabulary.Extension;
import org.gbif.api.vocabulary.MediaType;
import org.gbif.api.vocabulary.OccurrenceIssue;
import org.gbif.common.parsers.core.ParseResult;
import org.gbif.dwc.terms.DcTerm;
import org.gbif.dwc.terms.DwcTerm;
import org.gbif.dwc.terms.Term;
import org.gbif.occurrence.processor.interpreting.util.UrlParser;

import java.net.URI;
import java.util.Date;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;

import com.beust.jcommander.internal.Lists;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.google.common.collect.Iterables;
import org.apache.tika.Tika;
import org.apache.tika.mime.MimeType;
import org.apache.tika.mime.MimeTypeException;
import org.apache.tika.mime.MimeTypes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Interprets multi media extension records.
 */
public class MultiMediaInterpreter {

  private static final Logger LOG = LoggerFactory.getLogger(MultiMediaInterpreter.class);
  private static final Tika TIKA = new Tika();
  private static final MimeTypes MIME_TYPES = MimeTypes.getDefaultMimeTypes();
  private static final String HTML_TYPE = "text/html";
  private static final String[] MULTI_VALUE_DELIMITERS = {"|#DELIMITER#|", "|", ",", ";"};

  private MultiMediaInterpreter() {
  }

  public static void interpretMedia(VerbatimOccurrence verbatim, Occurrence occ) {
    // media via core term
    if (verbatim.hasVerbatimField(DwcTerm.associatedMedia)) {
      for (URI uri : parseAssociatedMedia(verbatim.getVerbatimField(DwcTerm.associatedMedia))) {
        if (uri == null) {
          occ.getIssues().add(OccurrenceIssue.MULTIMEDIA_URI_INVALID);

        } else {
          MediaObject m = new MediaObject();
          m.setIdentifier(uri);
          detectType(m);
          occ.getMedia().add(m);
        }
      }
    }

    // simple image or multimedia extension which are nearly identical
    if (verbatim.getExtensions().containsKey(Extension.IMAGE) || verbatim.getExtensions().containsKey(Extension.MULTIMEDIA)) {
      final Extension mediaExt = verbatim.getExtensions().containsKey(Extension.IMAGE) ? Extension.IMAGE : Extension.MULTIMEDIA;
      for (Map<Term, String> rec : verbatim.getExtensions().get(mediaExt)) {
        URI uri = UrlParser.parse(rec.get(DcTerm.identifier));
        URI link = UrlParser.parse(rec.get(DcTerm.references));
        // link or media uri must exist
        if (uri != null || link != null) {
          MediaObject m = new MediaObject();
          m.setIdentifier(uri);
          m.setReferences(link);
          m.setTitle(rec.get(DcTerm.title));
          m.setDescription(rec.get(DcTerm.description));
          m.setLicense(rec.get(DcTerm.license));
          m.setPublisher(rec.get(DcTerm.publisher));
          m.setContributor(rec.get(DcTerm.contributor));
          m.setSource(rec.get(DcTerm.source));
          m.setAudience(rec.get(DcTerm.audience));
          m.setRightsHolder(rec.get(DcTerm.rightsHolder));
          m.setCreator(rec.get(DcTerm.creator));
          m.setFormat(parseMimeType(rec.get(DcTerm.format)));
          if (rec.containsKey(DcTerm.created)) {
            ParseResult<Date> parsed = TemporalInterpreter.interpretDate(rec.get(DcTerm.created),
                                          TemporalInterpreter.VALID_RECORDED_DATE_RANGE,
                                          OccurrenceIssue.MULTIMEDIA_DATE_INVALID);
            m.setCreated(parsed.getPayload());
            occ.getIssues().addAll(parsed.getIssues());
          }

          detectType(m);
          occ.getMedia().add(m);

        } else {
          occ.getIssues().add(OccurrenceIssue.MULTIMEDIA_URI_INVALID);
        }
      }
    }
  }


  @VisibleForTesting
  protected static List<URI> parseAssociatedMedia(String associatedMedia) {
    List<URI> result = Lists.newArrayList();

    if (!Strings.isNullOrEmpty(associatedMedia)) {
      // first try to use the entire string
      URI uri = UrlParser.parse(associatedMedia);
      if (uri != null) {
        result.add(uri);

      } else {
        // try common delimiters
        int maxValidUrls = 0;
        for (String delimiter : MULTI_VALUE_DELIMITERS) {
          Splitter splitter = Splitter.on(delimiter).omitEmptyStrings().trimResults();
          String[] urls = Iterables.toArray(splitter.split(associatedMedia), String.class);
          // avoid parsing if we haven' actually split anything
          if (urls.length > 1) {
            List<URI> tmp = Lists.newArrayList();
            for (String url : urls) {
              uri = UrlParser.parse(url);
              if (uri != null) {
                tmp.add(uri);
              }
            }
            if (tmp.size() > maxValidUrls) {
              result = tmp;
              maxValidUrls = tmp.size();
            } else if (maxValidUrls > 0 && tmp.size() == maxValidUrls) {
              LOG.warn("Unclear what delimiter is being used for associatedMedia = {}", associatedMedia);
            }
          }
        }
      }
    }
    return result;
  }

  @VisibleForTesting
  protected static MediaObject detectType(MediaObject mo) {
    if (Strings.isNullOrEmpty(mo.getFormat())) {
      // derive from URI
      mo.setFormat(parseMimeType(mo.getIdentifier()));
    }

    // if MIME type is text/html make it a references link instead
    if (HTML_TYPE.equalsIgnoreCase(mo.getFormat()) && mo.getIdentifier() != null) {
      // make file URI the references link URL instead
      mo.setReferences(mo.getIdentifier());
      mo.setIdentifier(null);
      mo.setFormat(null);
    }

    if (!Strings.isNullOrEmpty(mo.getFormat())) {
      if (mo.getFormat().startsWith("image")) {
        mo.setType(MediaType.StillImage);
      } else if (mo.getFormat().startsWith("audio")) {
        mo.setType(MediaType.Sound);
      } else if (mo.getFormat().startsWith("video")) {
        mo.setType(MediaType.MovingImage);
      } else {
        LOG.debug("Unsupported media format {}", mo.getFormat());
      }
    }
    return mo;
  }

  /**
   * Parses a mime type using apache tika which can handle the following:
   * http://svn.apache.org/repos/asf/tika/trunk/tika-core/src/main/resources/org/apache/tika/mime/tika-mimetypes.xml
   */
  @VisibleForTesting
  protected static String parseMimeType(@Nullable String format) {
    if (format != null) {
      format = Strings.emptyToNull(format.trim().toLowerCase());
    }

    try {
      MimeType mime = MIME_TYPES.getRegisteredMimeType(format);
      if (mime != null) {
        return mime.getName();
      }

    } catch (MimeTypeException e) {
    }

    // verify this is a reasonable mime type
    return format == null || MimeType.isValid(format) ? format : null;
  }

  /**
   * Parses a mime type using apache tika which can handle the following:
   * http://svn.apache.org/repos/asf/tika/trunk/tika-core/src/main/resources/org/apache/tika/mime/tika-mimetypes.xml
   */
  @VisibleForTesting
  protected static String parseMimeType(@Nullable URI uri) {
    if (uri != null) {
      String mime = TIKA.detect(uri.toString());
      if (mime != null && mime.equalsIgnoreCase(MimeTypes.OCTET_STREAM)) {
        // links without any suffix default to OCTET STREAM, see:
        // http://dev.gbif.org/issues/browse/POR-2066
        return HTML_TYPE;
      }
      return mime;
    }
    return null;
  }
}
