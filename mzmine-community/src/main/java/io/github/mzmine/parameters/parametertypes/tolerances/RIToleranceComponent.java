/*
 * Copyright (c) 2004-2022 The MZmine Development Team
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
package io.github.mzmine.parameters.parametertypes.tolerances;

import io.github.mzmine.main.MZmineCore;
import io.github.mzmine.util.RIColumn;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.util.converter.NumberStringConverter;
import org.jetbrains.annotations.Nullable;

import java.text.NumberFormat;

/**
 *
 */
public class RIToleranceComponent extends HBox {

  private final ObservableList<RIColumn> toleranceTypes;
  private final NumberFormat format = NumberFormat.getIntegerInstance();
  private final TextFormatter<Number> textFormatter = new TextFormatter<>(
      new NumberStringConverter(format));
  private final TextField toleranceField;
  private final CheckBox shouldIgnoreWithoutRICheckBox;
  private final ComboBox<RIColumn> toleranceType;

  public RIToleranceComponent(ObservableList<RIColumn> riColumnTypes) {
    this.toleranceTypes = FXCollections.observableArrayList(riColumnTypes);

    setSpacing(5);
    toleranceField = new TextField();
    toleranceField.setTextFormatter(textFormatter);

    toleranceType = new ComboBox<>(toleranceTypes);
    toleranceType.getSelectionModel().select(0);

    shouldIgnoreWithoutRICheckBox = new CheckBox("Ignore library entries without RIs");
    shouldIgnoreWithoutRICheckBox.setSelected(true);

    getChildren().addAll(toleranceField, toleranceType, shouldIgnoreWithoutRICheckBox);
  }

  public RITolerance getValue() {
    RIColumn selectedColumnType = toleranceType.getValue();
    String valueString = toleranceField.getText();
    boolean ignoreWithoutRI = shouldIgnoreWithoutRICheckBox.isSelected();

    Integer tolerance = null;
    try {
        tolerance = Integer.parseInt(valueString);
    } catch (Exception e) {
      return null;
    }

    return new RITolerance(tolerance, selectedColumnType, ignoreWithoutRI);
  }

  public void setValue(@Nullable RITolerance value) {
    if (value == null) {
      toleranceField.setText("");
      toleranceType.getSelectionModel().select(0);
      shouldIgnoreWithoutRICheckBox.setSelected(true);
      return;
    }

    int tolerance = value.getTolerance();
    RIColumn selectedColumnType = value.getColumn();
    boolean ignoreWithoutRI = value.shouldIgnoreWithoutRI();

    toleranceType.setValue(selectedColumnType);
    shouldIgnoreWithoutRICheckBox.setSelected(ignoreWithoutRI);
    String valueString = String.valueOf(tolerance);
    toleranceField.setText(valueString);
  }

  public void setToolTipText(String toolTip) {
    toleranceField.setTooltip(new Tooltip(toolTip));
  }
}
