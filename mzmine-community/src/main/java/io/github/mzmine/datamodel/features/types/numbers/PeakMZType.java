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

package io.github.mzmine.datamodel.features.types.numbers;

import com.google.common.collect.Range;
import io.github.mzmine.datamodel.features.ModularDataModel;
import io.github.mzmine.datamodel.features.RowBinding;
import io.github.mzmine.datamodel.features.SimpleRowBinding;
import io.github.mzmine.datamodel.features.types.DataType;
import io.github.mzmine.datamodel.features.types.abstr.StringType;
import io.github.mzmine.datamodel.features.types.modifiers.BindingsType;
import io.github.mzmine.datamodel.features.types.modifiers.ExpandableType;
import io.github.mzmine.datamodel.features.types.numbers.abstr.DoubleType;
import io.github.mzmine.main.MZmineCore;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;
import org.jetbrains.annotations.NotNull;

public class PeakMZType extends StringType {

  public PeakMZType() {
    super();
  }

  @NotNull
  @Override
  public String getUniqueID() {
    // Never change the ID for compatibility during saving/loading of type
    return "peak_mz";
  }

  @Override
  public @NotNull String getHeaderString() {
    return "peak m/z";
  }

  @NotNull
  @Override
  public List<RowBinding> createDefaultRowBindings() {
    return List.of(new SimpleRowBinding(this, BindingsType.LIST));
  }

  @Override
  public boolean getDefaultVisibility() {
    return true;
  }

  @Override
  public Object evaluateBindings(@NotNull BindingsType bindingType,
      @NotNull List<? extends ModularDataModel> models) {
    Object result;
    if (bindingType == BindingsType.LIST) {
      Map<Long, Long> counts = new HashMap<>();
      for (var model : models) {
        String mz = model.get(this);
        if (mz != null) {
          Long key = Math.round(Double.parseDouble(mz));
          if (counts.containsKey(key)) {
            counts.put(key, counts.get(key) + 1);
          } else {
            counts.put(key, 1L);
          }
        }
      }

      result = counts.entrySet().stream()
          .sorted(Collections.reverseOrder(Entry.comparingByValue()))
          .map(entry -> String.format("%d (%d)", entry.getKey(), entry.getValue()))
          .collect(Collectors.joining(", "));
    } else {
      result = super.evaluateBindings(bindingType, models);
    }

    return result;
  }
}
