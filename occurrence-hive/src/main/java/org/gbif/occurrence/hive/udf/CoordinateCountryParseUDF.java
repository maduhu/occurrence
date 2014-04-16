package org.gbif.occurrence.hive.udf;

import org.gbif.api.vocabulary.Country;
import org.gbif.common.parsers.core.ParseResult;
import org.gbif.occurrence.processor.interpreting.result.CoordinateCountry;
import org.gbif.occurrence.processor.interpreting.util.CoordinateInterpreter;
import org.gbif.occurrence.processor.interpreting.util.CountryInterpreter;

import java.util.Arrays;
import java.util.List;

import com.beust.jcommander.internal.Lists;
import com.google.common.base.Strings;
import org.apache.hadoop.hive.ql.exec.Description;
import org.apache.hadoop.hive.ql.exec.UDFArgumentException;
import org.apache.hadoop.hive.ql.metadata.HiveException;
import org.apache.hadoop.hive.ql.udf.generic.GenericUDF;
import org.apache.hadoop.hive.serde2.objectinspector.ObjectInspector;
import org.apache.hadoop.hive.serde2.objectinspector.ObjectInspectorConverters;
import org.apache.hadoop.hive.serde2.objectinspector.ObjectInspectorFactory;
import org.apache.hadoop.hive.serde2.objectinspector.primitive.PrimitiveObjectInspectorFactory;

/**
 * A UDF that uses the GBIF API to verify coordinates look sensible.
 * If coordinates aren't available then country is interpreted only.
 * If coordinates are present, then country and coordinates are returned only if they don't contradict, otherwise they
 * are BOTH dropped.
 * Note: This is used for the GBIF EU BON analysis.
 */
@Description(name = "parseCoordinates", value = "_FUNC_(latitude, longitude, verbatim_country)")
public class CoordinateCountryParseUDF extends GenericUDF {

  private ObjectInspectorConverters.Converter[] converters;

  @Override
  public Object evaluate(DeferredObject[] arguments) throws HiveException {
    assert arguments.length == 3;

    // Interpret the country to pass in to the geo lookup
    String country = arguments[2].get() == null ? null : converters[2].convert(arguments[2].get()).toString();
    Country interpretedCountry = Country.UNKNOWN;
    if (country != null) {
      ParseResult<Country> r = CountryInterpreter.interpretCountry(country);
      if (r.isSuccessful() && r.getPayload() != null) {
        interpretedCountry = r.getPayload();
      }
    }
    String iso = Country.UNKNOWN == interpretedCountry ? null : interpretedCountry.getIso2LetterCode();

    List<Object> result = Lists.newArrayList(3);

    if (arguments[0].get() == null || arguments[1].get() == null
      || Strings.isNullOrEmpty(arguments[0].get().toString()) || Strings.isNullOrEmpty(arguments[1].get().toString())
      || arguments[1].get().toString().length() == 0) {
      result.add(null);
      result.add(null);
      result.add(iso); // no coords to dispute the iso
      return result;
    }

    String latitude = converters[0].convert(arguments[0].get()).toString();
    String longitude = converters[1].convert(arguments[1].get()).toString();
    ParseResult<CoordinateCountry> response =
      CoordinateInterpreter.interpretCoordinates(latitude, longitude, interpretedCountry);

    // it is either all good, or it's considered bad
    if (response != null && response.isSuccessful() && response.getIssues().isEmpty()) {
      CoordinateCountry cc = response.getPayload();
      result.add(cc.getLatitude());
      result.add(cc.getLongitude());
      result.add(iso);

    } else {
      result.add(null);
      result.add(null);
      result.add(null);
    }
    return result;
  }

  @Override
  public String getDisplayString(String[] strings) {
    assert strings.length == 3;
    return "parseCoordinates(" + strings[0] + ", " + strings[1] + ", " + strings[2] + ")";
  }

  @Override
  public ObjectInspector initialize(ObjectInspector[] arguments) throws UDFArgumentException {
    if (arguments.length != 3) {
      throw new UDFArgumentException("parseCoordinates takes three arguments");
    }

    converters = new ObjectInspectorConverters.Converter[arguments.length];
    for (int i = 0; i < arguments.length; i++) {
      converters[i] = ObjectInspectorConverters
        .getConverter(arguments[i], PrimitiveObjectInspectorFactory.writableStringObjectInspector);
    }

    return ObjectInspectorFactory
      .getStandardStructObjectInspector(Arrays.asList("latitude", "longitude", "country"), Arrays
        .<ObjectInspector>asList(PrimitiveObjectInspectorFactory.javaDoubleObjectInspector,
          PrimitiveObjectInspectorFactory.javaDoubleObjectInspector,
          PrimitiveObjectInspectorFactory.javaStringObjectInspector));
  }
}
