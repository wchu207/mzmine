/*
 * Copyright (c) 2004-2023 The MZmine Development Team
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

package io.github.mzmine.modules.io.export_features_csv_custom;

import com.opencsv.CSVWriter;
import io.github.mzmine.datamodel.RawDataFile;
import io.github.mzmine.datamodel.features.*;
import io.github.mzmine.datamodel.features.types.DataType;
import io.github.mzmine.datamodel.features.types.DataTypes;
import io.github.mzmine.datamodel.features.types.annotations.*;
import io.github.mzmine.datamodel.features.types.modifiers.SubColumnsFactory;
import io.github.mzmine.datamodel.features.types.numbers.*;
import io.github.mzmine.datamodel.features.types.numbers.scores.ExplainedIntensityPercentType;
import io.github.mzmine.datamodel.features.types.numbers.scores.SimilarityType;
import io.github.mzmine.modules.io.export_features_gnps.fbmn.FeatureListRowsFilter;
import io.github.mzmine.parameters.ParameterSet;
import io.github.mzmine.taskcontrol.AbstractTask;
import io.github.mzmine.taskcontrol.ProcessedItemsCounter;
import io.github.mzmine.taskcontrol.TaskStatus;
import io.github.mzmine.util.files.FileAndPathUtil;
import org.apache.commons.io.FilenameUtils;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

public class CustomCSVExportModularTask extends AbstractTask implements ProcessedItemsCounter {

  public static final String DATAFILE_PREFIX = "datafile";
  private static final Logger logger = Logger.getLogger(CustomCSVExportModularTask.class.getName());
  private final ModularFeatureList[] featureLists;
  // parameter values
  private final File fileName;
  private final FeatureListRowsFilter rowFilter;
  private final ParameterSet parameters;

  private final List<Class<? extends DataType<?>>> rowTypes = List.of(
          IDType.class,
          RTType.class,
          RTRangeType.class,
          MZType.class,
          MZRangeType.class,
          RIType.class,
          RIMinType.class,
          RIMaxType.class,
          RIDiffType.class,
          HeightType.class,
          AreaType.class,
          IntensityRangeType.class,
          FragmentScanNumbersType.class,
          CustomSpectralLibraryMatchesLocalSummaryType.class,
          CustomSpectralLibraryMatchesGlobalSummaryType.class,
          SpectralLibraryMatchesType.class
  );
  private final Map<Class<? extends DataType<?>>, List<Class<? extends DataType>>> rowSubtypes = Map.of(
          RTRangeType.class, List.of(RTRangeType.class, MZType.class),
          MZRangeType.class, List.of(MZRangeType.class, MZType.class),
          IntensityRangeType.class, List.of(IntensityRangeType.class, MZType.class),
          SpectralLibraryMatchesType.class, List.of(CompoundNameType.class, MatchingSignalsType.class, SimilarityType.class, ExplainedIntensityPercentType.class, CommentType.class)
  );
  private ArrayList<Boolean> rowMask = null;

  private final List<Class<? extends DataType<?>>> featureTypes = List.of(
          MZType.class,
          RTType.class,
          RIType.class,
          RIScaleType.class,
          HeightType.class,
          AreaType.class,
          SpectralLibraryMatchesType.class
  );
  private final Map<Class<? extends DataType<?>>, List<Class<? extends DataType>>> featureSubtypes = Map.of(
          SpectralLibraryMatchesType.class, List.of(CompoundNameType.class, MatchingSignalsType.class, SimilarityType.class, ExplainedIntensityPercentType.class, CommentType.class)
  );
  private ArrayList<Boolean> featureMask = null;

  // track number of exported items
  private final AtomicInteger exportedRows = new AtomicInteger(0);
  private int processedRows = 0, nRows = 0;

  public CustomCSVExportModularTask(ParameterSet parameters, @NotNull Instant moduleCallDate) {
    super(null, moduleCallDate); // no new data stored -> null
    this.featureLists = parameters.getParameter(CustomCSVExportModularParameters.featureLists).getValue()
            .getMatchingFeatureLists();
    fileName = parameters.getParameter(CustomCSVExportModularParameters.filename).getValue();
    this.rowFilter = parameters.getParameter(CustomCSVExportModularParameters.filter).getValue();
    this.parameters = parameters;
  }

  @Override
  public int getProcessedItems() {
    return exportedRows.get();
  }

  @Override
  public double getFinishedPercentage() {
    if (nRows == 0) {
      return 0;
    }
    return (double) processedRows / (double) nRows;
  }

  @Override
  public String getTaskDescription() {
    return "Exporting feature list(s) " + Arrays.toString(featureLists)
            + " to CSV file(s) (new format)";
  }

  @Override
  public void run() {
    setStatus(TaskStatus.PROCESSING);

    // Shall export several files?
    String plNamePattern = "{}";
    boolean substitute = fileName.getPath().contains(plNamePattern);

    if (!substitute && featureLists.length > 1) {
      setErrorMessage("""
              Cannot export multiple feature lists to the same CSV file. Please use "{}" pattern in filename.\
              This will be replaced with the feature list name to generate one file per feature list.
              """);
      setStatus(TaskStatus.ERROR);
      return;
    }

    // Total number of rows
    for (ModularFeatureList featureList : featureLists) {
      nRows += featureList.getNumberOfRows();
    }

    // Process feature lists
    for (ModularFeatureList featureList : featureLists) {
      // Cancel?
      if (isCanceled()) {
        return;
      }
      // check concurrent modification during export

      // Filename
      File curFile = fileName;
      if (substitute) {
        // Cleanup from illegal filename characters
        String cleanPlName = featureList.getName().replaceAll("[^a-zA-Z0-9.-]", "_");
        // Substitute
        String newFilename = fileName.getPath()
                .replaceAll(Pattern.quote(plNamePattern), cleanPlName);
        curFile = new File(newFilename);
      }
      curFile = FileAndPathUtil.getRealFilePath(curFile, "csv");

      // Open file

      try (CSVWriter writer = new CSVWriter(Files.newBufferedWriter(curFile.toPath(),
              StandardCharsets.UTF_8))) {
        exportFeatureList(featureList, writer);

      } catch (IOException e) {
        setStatus(TaskStatus.ERROR);
        setErrorMessage("Could not open file " + curFile + " for writing.");
        logger.log(Level.WARNING, String.format(
                "Error writing new CSV format to file: %s for feature list: %s. Message: %s",
                curFile.getAbsolutePath(), featureList.getName(), e.getMessage()), e);
        return;
      }

      int numRows = featureList.getNumberOfRows();
      long numFeatures = featureList.streamFeatures().count();
      long numMS2 = featureList.stream().filter(row -> row.hasMs2Fragmentation()).count();

      checkConcurrentModification(featureList, numRows, numFeatures, numMS2);

      File summaryFile = new File(FilenameUtils.removeExtension(curFile.getAbsolutePath()) + "_summary.csv");
      try (CSVWriter writer = new CSVWriter(Files.newBufferedWriter(summaryFile.toPath(),
              StandardCharsets.UTF_8))) {
        exportFeatureListSummary(featureList, writer);

      } catch (IOException e) {
        setStatus(TaskStatus.ERROR);
        setErrorMessage("Could not open file " + curFile + " for writing.");
        logger.log(Level.WARNING, String.format(
                "Error writing new CSV format to file: %s for feature list: %s. Message: %s",
                summaryFile.getAbsolutePath(), featureList.getName(), e.getMessage()), e);
        return;
      }

      numRows = featureList.getNumberOfRows();
      numFeatures = featureList.streamFeatures().count();
      numMS2 = featureList.stream().filter(row -> row.hasMs2Fragmentation()).count();

      checkConcurrentModification(featureList, numRows, numFeatures, numMS2);


      checkConcurrentModification(featureList, numRows, numFeatures, numMS2);
      if (parameters != null) { // if this is null, the external constructor was used.
        featureList.getAppliedMethods().add(
                new SimpleFeatureListAppliedMethod(CustomCSVExportModularModule.class, parameters,
                        getModuleCallDate()));
      }

      // If feature list substitution pattern wasn't found,
      // treat one feature list only
      if (!substitute) {
        break;
      }
    }

    if (getStatus() == TaskStatus.PROCESSING) {
      setStatus(TaskStatus.FINISHED);
    }
  }

  private void checkTypes(ModularFeatureList flist) {
    rowMask = new ArrayList<>();
    for (Class<? extends DataType<?>> type : rowTypes) {
      rowMask.add(flist.hasRowType(type));
    }

    featureMask = new ArrayList<>();
    for (Class<? extends DataType<?>> type : featureTypes) {
      featureMask.add(flist.hasFeatureType(type));
    }


  }

  @SuppressWarnings("rawtypes")
  private void exportFeatureList(ModularFeatureList flist, CSVWriter writer)
          throws IOException {
    checkTypes(flist);
    List<String> headers = getHeaders(flist);
    writer.writeNext(headers.toArray(new String[]{}));
    for (FeatureListRow row : flist.getRows()) {
      List<String> allRowEntries = new ArrayList<>();
      allRowEntries.addAll(extractTypeValues(row, rowTypes, rowSubtypes, rowMask));
      for (RawDataFile raw : flist.getRawDataFiles()) {
        if (row.hasFeature(raw)) {
          ModularFeature feat = (ModularFeature) row.getFeature(raw);
          allRowEntries.addAll(extractTypeValues(feat, featureTypes, featureSubtypes, featureMask));
        } else {
          allRowEntries.addAll(createBlanks(featureTypes, featureSubtypes, featureMask));
        }
      }
      writer.writeNext(allRowEntries.toArray(new String[]{}));
      processedRows += 1;
    }
  }

  private void exportFeatureListSummary(ModularFeatureList flist, CSVWriter writer)
          throws IOException {
    List<RawDataFile> raws = flist.getRawDataFiles();
    List<String> currentRow = new ArrayList<>();
    currentRow.add("row id");
    currentRow.addAll(raws.stream().map(RawDataFile::getName).toList());

    writer.writeNext(currentRow.toArray(new String[]{}));
    for (var row : flist.getRows()) {
      currentRow.clear();;
      currentRow.add(row.getID().toString());
      for (var raw : raws) {
        if (row.hasFeature(raw)) {
          Integer ri = row.getFeature(raw).getRI();
          currentRow.add(ri != null ? ri.toString() : "NO RI");
        } else {
          currentRow.add("");
        }
      }
      writer.writeNext(currentRow.toArray(new String[]{}));
    }
  }

  private List<String> createBlanks(List<Class<? extends DataType<?>>> types, Map<Class<? extends DataType<?>>, List<Class<? extends DataType>> > subTypes, List<Boolean> mask) {

    ArrayList<String> entries = new ArrayList<>();
    for (int typeIndex = 0; typeIndex < types.size(); typeIndex++) {
      if (mask.get(typeIndex)) {
        DataType type = DataTypes.get(types.get(typeIndex));
        if (!(type instanceof SubColumnsFactory)) {
          entries.add("");
        } else {
          entries.addAll(getSubTypeValuesToExport(null, (SubColumnsFactory) type, subTypes.get(types.get(typeIndex))));
        }
      }
    }
    return entries;
  }

  private List<String> extractTypeValues(ModularDataModel data, List<Class<? extends DataType<?>>> types, Map<Class<? extends DataType<?>>, List<Class<? extends DataType>>> subTypes, List<Boolean> mask) {

    ArrayList<String> entries = new ArrayList<>();

    for (int typeIndex = 0; typeIndex < types.size(); typeIndex++) {
      if (mask.get(typeIndex)) {
        DataType type = DataTypes.get(types.get(typeIndex));
        if (!(type instanceof SubColumnsFactory)) {
          Object value = data.get(type);
          if (value == null) {
            value = type.getDefaultValue();
          }
          try {
            entries.add(type.getFormattedExportString(value));
          } catch (Exception e) {
            entries.add("");
            logger.log(Level.FINEST,
                    "Cannot format value of type " + type.getClass().getName() + " value: " + value, e);
          }

        } else if (subTypes.containsKey(types.get(typeIndex))) {
          Object value = data.get(type);
          List<String> currentSubTypes = getSubTypeValuesToExport(value, (SubColumnsFactory) type, subTypes.get(types.get(typeIndex)));
          entries.addAll(currentSubTypes);
        }
      }
    }
    return entries;
  }
  private List<String> getHeaders(ModularFeatureList flist) {
    List<String> headers = new ArrayList<>();
    for (int typeIndex = 0; typeIndex < rowTypes.size(); typeIndex++) {
      if (rowMask.get(typeIndex)) {
        DataType type = DataTypes.get(rowTypes.get(typeIndex));
        if (!(type instanceof SubColumnsFactory)) {
          headers.add(type.getHeaderString().toLowerCase());
        } else {
          List<String> subheaders = getSubTypeHeaders((SubColumnsFactory) type, rowSubtypes.get(rowTypes.get(typeIndex))).stream().map(s -> type.getHeaderString() + ":" + s).map(String::toLowerCase).toList();
          headers.addAll(subheaders);
        }
      }
    }
    for(var raw : flist.getRawDataFiles()) {
      for (int typeIndex = 0; typeIndex < featureTypes.size(); typeIndex++) {
        if (featureMask.get(typeIndex)) {
          DataType type = DataTypes.get(featureTypes.get(typeIndex));
          if (!(type instanceof SubColumnsFactory)) {
            headers.add((raw.getName() + ":" + type.getHeaderString()).toLowerCase());
          } else {
            List<String> subheaders = getSubTypeHeaders((SubColumnsFactory) type, featureSubtypes.get(featureTypes.get(typeIndex))).stream().map(s -> raw.getName()  + ":" + type.getHeaderString() + ":" + s).map(String::toLowerCase).toList();
            headers.addAll(subheaders);
          }
        }
      }
    }
    return headers;
  }


  private void checkConcurrentModification(FeatureList featureList, int numRows, long numFeatures,
                                           long numMS2) {
    final int numRowsEnd = featureList.getNumberOfRows();
    final long numFeaturesEnd = featureList.streamFeatures().count();
    final long numMS2End = featureList.stream().filter(row -> row.hasMs2Fragmentation()).count();

    if (numRows != numRowsEnd) {
      throw new ConcurrentModificationException(String.format(
              "Detected modification to number of ROWS during featurelist (%s) CSV export old=%d new=%d",
              featureList.getName(), numRows, numRowsEnd));
    }
    if (numFeatures != numFeaturesEnd) {
      throw new ConcurrentModificationException(String.format(
              "Detected modification to number of ROWS during featurelist (%s) CSV export old=%d new=%d",
              featureList.getName(), numFeatures, numFeaturesEnd));
    }
    if (numMS2 != numMS2End) {
      throw new ConcurrentModificationException(String.format(
              "Detected modification to number of ROWS WITH MS2 during featurelist (%s) CSV export old=%d new=%d",
              featureList.getName(), numMS2, numMS2End));
    }
  }

  private List<String> getSubTypeHeaders(SubColumnsFactory factory, List<Class<? extends DataType>> allowedSubTypes) {
    return getSubTypeColumnNumbers(null, factory, allowedSubTypes).stream().map(c -> c != null ? factory.getHeader(c) : "").toList();
  }

  private List<String> getSubTypeValuesToExport(Object value, SubColumnsFactory factory, List<Class<? extends DataType>> allowedSubTypes) {
    return getSubTypeColumnNumbers(value, factory, allowedSubTypes).stream().map(col -> col != null ? factory.getFormattedSubColExportValue(col, value) : "").toList();
  }

  private List<Integer> getSubTypeColumnNumbers(Object value, SubColumnsFactory factory, List<Class<? extends DataType>> allowedSubTypes) {
    List<Integer> colNums = new ArrayList<>();
    for (var type : allowedSubTypes) {
      boolean found = false;
      for (int i = 0; i < factory.getNumberOfSubColumns(); i++) {
        if (!colNums.contains(i) && type.isInstance(factory.getType(i))) {
          colNums.add(i);
          found = true;
          break;
        }
      }
      if (!found) {
        colNums.add(null);
      }
    }
    return colNums;
  }
}
