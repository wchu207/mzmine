/*
 * Copyright (c) 2004-2024 The MZmine Development Team
 *
 * Permission is hereby granted, free of charge, to any person
 * obtaining a copy of this software and associated documentation
 * files (the "Software"), to deal in the Software without
 * restriction, including without limitation the rights to use,
 * copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the
 * Software is furnished to do so, subject to the following
 * conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES
 * OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 * NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT
 * HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
 * FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR
 * OTHER DEALINGS IN THE SOFTWARE.
 */

package io.github.mzmine.modules.io.import_features_json;

import com.google.common.collect.Range;
import com.google.common.primitives.Doubles;
import io.github.mzmine.datamodel.*;
import io.github.mzmine.datamodel.features.*;
import io.github.mzmine.datamodel.features.types.DataType;
import io.github.mzmine.datamodel.features.types.DetectionType;
import io.github.mzmine.datamodel.features.types.annotations.CustomSpectralLibraryMatchesLocalSummaryType;
import io.github.mzmine.datamodel.features.types.annotations.RIScaleType;
import io.github.mzmine.datamodel.features.types.numbers.*;
import io.github.mzmine.datamodel.impl.SimplePseudoSpectrum;
import io.github.mzmine.datamodel.impl.SimpleScan;
import io.github.mzmine.datamodel.impl.masslist.ScanPointerMassList;
import io.github.mzmine.parameters.ParameterSet;
import io.github.mzmine.taskcontrol.AbstractTask;
import io.github.mzmine.taskcontrol.TaskStatus;
import io.github.mzmine.util.MemoryMapStorage;
import io.github.mzmine.util.scans.similarity.SpectralSimilarity;
import io.github.mzmine.util.spectraldb.entry.DBEntryField;
import io.github.mzmine.util.spectraldb.entry.SpectralDBAnnotation;
import io.github.mzmine.util.spectraldb.entry.SpectralDBEntry;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;

import java.io.File;
import java.io.FileReader;
import java.time.Instant;
import java.util.*;
import java.util.stream.StreamSupport;

public class JSONImportTask extends AbstractTask {

  // parameter values
  private final MZmineProject project;
  private final ParameterSet parameters;
  private final File inputFile;
  private double finishedPercentage = 0.0;
  private final Map<String, Class<? extends DataType<?>>> keyMap = null;
  private RawDataFile rawDataFile = null;
  private ModularFeatureList featureList = null;


  JSONImportTask(MZmineProject project, ParameterSet parameters, File inputFile,
                 @Nullable MemoryMapStorage storage, @NotNull Instant moduleCallDate) {
    super(storage, moduleCallDate);
    this.project = project;
    this.parameters = parameters;
    this.inputFile = inputFile;
  }

  @Override
  public String getTaskDescription() {
    return "Loading feature list from JSON file";
  }

  @Override
  public void cancel() {
    super.cancel();

  }

  @Override
  public double getFinishedPercentage() {
    return finishedPercentage;
  }

  @Override
  public void run() {
    setStatus(TaskStatus.PROCESSING);
    List<Scan> selectedScans = new ArrayList<>();
    try {
      JSONArray rowsObjs = new JSONArray(new JSONTokener(new FileReader(inputFile)));
      if (!rowsObjs.isEmpty()) {
        rawDataFile = project.getDataFileByName(rowsObjs.getJSONObject(0).getString("raw_file_name"));
        if (rawDataFile != null) {
          boolean success = initializeFeatureList(rawDataFile, this.storage);
          if (success) {
            List<ModularFeatureListRow> rows = StreamSupport.stream(rowsObjs.spliterator(), false).map(this::parseLine).toList();
            for (var row : rows) {
              featureList.addRow(row);
              row.getFeatures().stream().flatMap(f -> f.getScanNumbers().stream()).forEach(selectedScans::add);
            }
            project.addFeatureList(featureList);
            featureList.setSelectedScans(rawDataFile, selectedScans);
          }
        }

      }


      // Finish
      setStatus(TaskStatus.FINISHED);
      finishedPercentage = 1.0;


    } catch (Exception e) {
      e.printStackTrace();
      setStatus(TaskStatus.ERROR);
      setErrorMessage("Could not import data from " + inputFile + ": " + e.getMessage());
    }
  }

  private boolean initializeFeatureList(RawDataFile rawDataFile, MemoryMapStorage storage) {
    try {
      featureList = new ModularFeatureList(rawDataFile.getName(), storage, rawDataFile);
      featureList.addFeatureType(new DetectionType());
      featureList.addFeatureType(new MZType());
      featureList.addFeatureType(new MZRangeType());
      featureList.addFeatureType(new RTType());
      featureList.addFeatureType(new RTRangeType());

      featureList.addFeatureType(new HeightType());
      featureList.addFeatureType(new AreaType());

      featureList.addFeatureType(new IntensityRangeType());
      featureList.addFeatureType(new FwhmType());
      featureList.addFeatureType(new TailingFactorType());
      featureList.addFeatureType(new AsymmetryFactorType());
      featureList.addFeatureType(new FragmentScanNumbersType());

      featureList.addFeatureType(new RIType());
      featureList.addFeatureType(new RIMaxType());
      featureList.addFeatureType(new RIMinType());
      featureList.addFeatureType(new RIDiffType());

      featureList.addFeatureType(new CustomSpectralLibraryMatchesLocalSummaryType());
      return true;
    } catch (Exception e) {
      return false;
    }
  }

  private ModularFeatureListRow parseLine(Object rowObj) {
    try {
      if (rowObj instanceof JSONObject rowAsJSON) {
        int rowId = rowAsJSON.getInt("row_id");
        JSONArray scanJSONArray = rowAsJSON.getJSONArray("scans");
        List<JSONObject> scanObjs = new ArrayList<JSONObject>();
        for (int i = 0; i < scanJSONArray.length(); i++) {
          if (scanJSONArray.getJSONObject(i).getInt("scan_no") != -1) {
            scanObjs.add(scanJSONArray.getJSONObject(i));
          }
        }

        PseudoSpectrumType pseudoType = PseudoSpectrumType.valueOf(rowAsJSON.getString("pseudospectrum"));
        Scan fragScan = parseFragmentScan(rowAsJSON, pseudoType);

        List<Scan> scans = new ArrayList<>(scanObjs.parallelStream().map(this::parseScan).sorted(Comparator.comparing(scan -> scan.getRetentionTime())).toList());
        FeatureStatus status = FeatureStatus.valueOf(rowAsJSON.getString("status"));

        double rt = rowAsJSON.getDouble("rt");
        double mz = rowAsJSON.getDouble("mz");
        double area = rowAsJSON.getDouble("area");
        double height = rowAsJSON.getDouble("height");


        JSONArray mzJSONArray = rowAsJSON.getJSONArray("mz_list");
        JSONArray intensityJSONArray = rowAsJSON.getJSONArray("intensity_list");

        List<Double> mzList = new ArrayList<Double>();
        List<Double> intensityList = new ArrayList<Double>();
        for (int i = 0; i < mzJSONArray.length(); i++) {
          mzList.add(mzJSONArray.getDouble(i));
          intensityList.add(intensityJSONArray.getDouble(i));
        }


        JSONArray rtRangeArray = rowAsJSON.getJSONArray("rt_range");
        Range<Float> rtRange = Range.closed(rtRangeArray.getFloat(0), rtRangeArray.getFloat(1));

        JSONArray mzRangeArray = rowAsJSON.getJSONArray("mz_range");
        Range<Double> mzRange = Range.closed(mzRangeArray.getDouble(0), mzRangeArray.getDouble(1));

        JSONArray intensityRangeArray = rowAsJSON.getJSONArray("intensity_range");
        Range<Float> intensityRange = Range.closed(intensityRangeArray.getFloat(0), intensityRangeArray.getFloat(1));
        int bestScanNumber = rowAsJSON.getInt("best_scan_no");

        Scan representativeScan = null;
        for (Scan scan : scans) {
          if (scan.getScanNumber() == bestScanNumber) {
            representativeScan = scan;
          }
        }


        ModularFeature feature = new ModularFeature(featureList, rawDataFile, mz, (float) rt, (float) height, (float) area,
                scans, Doubles.toArray(mzList), Doubles.toArray(intensityList), status, representativeScan, List.of(fragScan),
                rtRange, mzRange, intensityRange);

        if (rowAsJSON.has("ri")) {
          feature.set(RIType.class, rowAsJSON.getFloat("ri"));
          feature.set(RIScaleType.class, rowAsJSON.getString("ri_scale"));
        }

        if (rowAsJSON.has("library_match")) {
          JSONObject entryJSON = rowAsJSON.getJSONObject("library_match").getJSONObject("entry");
          JSONArray libraryJSONMZs = entryJSON.getJSONArray("mz_list");
          JSONArray libraryJSONIntensities = entryJSON.getJSONArray("intensity_list");

          List<Double> libraryMZs = new ArrayList<Double>();
          List<Double> libraryIntensities = new ArrayList<Double>();
          for (int i = 0; i < libraryJSONMZs.length(); i++) {
            libraryMZs.add(libraryJSONMZs.getDouble(i));
            libraryIntensities.add(libraryJSONIntensities.getDouble(i));
          }

          Set<DBEntryField> stringKeys = Set.of(
                  DBEntryField.NAME,
                  DBEntryField.FORMULA,
                  DBEntryField.INSTRUMENT,
                  DBEntryField.INSTRUMENT_TYPE,
                  DBEntryField.INCHIKEY,
                  DBEntryField.MS_LEVEL,
                  DBEntryField.COMMENT
          );

          HashMap<DBEntryField, Object> fields = new HashMap<>();
          for (var key : stringKeys) {
            String keyString = key.getMZmineJsonID();
            if (entryJSON.keySet().contains(keyString)) {
              fields.put(key, (Object) entryJSON.getString(keyString));
            }
          }
          SpectralDBEntry entry = new SpectralDBEntry(null, Doubles.toArray(libraryMZs), Doubles.toArray(libraryIntensities), fields);


          JSONObject similarityJSON = rowAsJSON.getJSONObject("library_match").getJSONObject("similarity");

          SpectralSimilarity sim = new SpectralSimilarity(similarityJSON.getString("name"), similarityJSON.getFloat("score"), similarityJSON.getInt("overlap"), similarityJSON.getFloat("explained_intensity"));
          feature.addSpectralLibraryMatches(List.of(new SpectralDBAnnotation(entry, sim, fragScan, null, null, null)));
        }

        return new ModularFeatureListRow(featureList, rowId, feature);
      } else {
        return null;
      }
    } catch (Exception e) {
      throw e;
    }
  }

  private Scan parseScan(JSONObject scanAsJSON) {
    int scanNumber = scanAsJSON.getInt("scan_no");
    int msLevel = scanAsJSON.getInt("ms_level");
    String scanDefinition = scanAsJSON.getString("scan_definition");
    float rt = scanAsJSON.getFloat("rt");
    PolarityType polarity = PolarityType.parseFromString(scanAsJSON.getString("polarity"));
    MassSpectrumType spectrum = MassSpectrumType.valueOf(scanAsJSON.getString("spectrum_type"));
    JSONArray scanJSONArray = scanAsJSON.getJSONArray("mz_range");
    Range<Double> scanRange = Range.closed(scanJSONArray.getDouble(0), scanJSONArray.getDouble(1));

    List<Double> mzList = new ArrayList<>();
    List<Double> intensityList = new ArrayList<>();

    JSONArray mzJsonArray = scanAsJSON.getJSONArray("mz_list");
    JSONArray intensityJsonArray = scanAsJSON.getJSONArray("intensity_list");

    for (int i = 0; i < mzJsonArray.length(); i++) {
      mzList.add(mzJsonArray.getDouble(i));
      intensityList.add(intensityJsonArray.getDouble(i));
    }
    SimpleScan scan = new SimpleScan(this.rawDataFile, scanNumber, msLevel, rt, null, Doubles.toArray(mzList), Doubles.toArray(intensityList), spectrum, polarity, scanDefinition, scanRange);
    scan.addMassList(new ScanPointerMassList(scan));
    return scan;
  }

  private Scan parseFragmentScan(JSONObject rowJSON, PseudoSpectrumType pseudoSpectrumType) {
    int msLevel = rowJSON.getInt("ms_level");
    String scanDefinition = rowJSON.getString("scan_definition");
    float rt = rowJSON.getFloat("rt");
    PolarityType polarity = PolarityType.parseFromString(rowJSON.getString("polarity"));

    List<Double> mzList = new ArrayList<>();
    List<Double> intensityList = new ArrayList<>();

    JSONArray peaks = rowJSON.getJSONArray("peaks");
    for (int i = 0; i < peaks.length(); i++) {
      mzList.add(peaks.getJSONArray(i).getDouble(0));
      intensityList.add(peaks.getJSONArray(i).getDouble(1));
    }

    SimplePseudoSpectrum scan = new SimplePseudoSpectrum(rawDataFile, msLevel, rt, null, Doubles.toArray(mzList), Doubles.toArray(intensityList), polarity, scanDefinition, pseudoSpectrumType);
    scan.addMassList(new ScanPointerMassList(scan));
    return scan;
  }

}