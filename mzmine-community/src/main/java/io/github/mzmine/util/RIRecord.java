/*
 * Copyright (c) 2004-2024 The mzmine Development Team
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

package io.github.mzmine.util;

import java.util.EnumMap;
import java.util.Map;
import java.util.logging.Logger;


// This holds information from the RI field in NIST libraries for gas chromatography
//   The RI field is of form "s=RI/n_samples/CI n=RI/n_samples/CI p=RI/n_samples/CI" (CI denotes confidence interval)
//   s denotes semipolar, n denotes nonpolar, and p denotes polar
//   If there are 0 samples for s or n or p, then that part is skipped
//   If there is one sample, then n_samples and CI are skipped
//   Unclear if other formats differ


public class RIRecord
{
    private static final Logger logger = Logger.getLogger(RIRecord.class.getName());
    private Map<RIColumn, RIRecordPart> map;

    public RIRecord(Map<RIColumn, RIRecordPart> map) {
        this.map = map;
    }

    public RIRecord(String record) {
        this.map = parse(record);
    }

    public String toString() {
        StringBuilder sb = new StringBuilder();
        boolean hasPrev = false;

        if (map.containsKey(RIColumn.SEMIPOLAR)) {
            Float s_ri = map.get(RIColumn.SEMIPOLAR).ri();
            Integer s_count = map.get(RIColumn.SEMIPOLAR).count();
            Float s_ci = map.get(RIColumn.SEMIPOLAR).ci();

            // s_ri != null should always be true
            sb.append(s_ri != null ? String.format("s=%d", Math.round(s_ri)) : "");
            sb.append((s_ri != null && s_count != null && s_ci != null) ? String.format("/%d/%d", s_count, Math.round(s_ci)) : "");
            hasPrev = s_ri != null;
        }

        if (map.containsKey(RIColumn.NONPOLAR)) {
            Float n_ri = map.get(RIColumn.NONPOLAR).ri();
            Integer n_count = map.get(RIColumn.NONPOLAR).count();
            Float n_ci = map.get(RIColumn.NONPOLAR).ci();

            // n_ri != null should always be true
            sb.append(hasPrev && map.get(RIColumn.SEMIPOLAR).ri() != null && n_ri != null ? " " : "");
            sb.append(n_ri != null ? String.format("n=%d", Math.round(n_ri)) : "");
            sb.append((n_ri != null && n_count != null && n_ci != null) ? String.format("/%d/%d", n_count, Math.round(n_ci)) : "");
            hasPrev = hasPrev || n_ri != null;
        }

        return sb.toString();
    }
    private Map<RIColumn, RIRecordPart> parse(String line) {
        String[] records = line.split("\\s+");

        Map<RIColumn, RIRecordPart> recordsMap = new EnumMap<>(RIColumn.class);

        for(String record : records) {
            boolean isSingleValue = false;
            try {
                Float.parseFloat(record);
                recordsMap.put(RIColumn.DEFAULT, new RIRecordPart(Float.parseFloat(record), null, null));
            } catch (NumberFormatException ne)  {
                RIColumn currentType = null;
                if (record.startsWith("a=")) {
                    currentType = RIColumn.DEFAULT;
                }
                else if (record.startsWith("s=")) {
                    currentType = RIColumn.SEMIPOLAR;
                } else if (record.startsWith("n=")) {
                    currentType = RIColumn.NONPOLAR;
                } else if (record.startsWith("p=")) {
                    currentType = RIColumn.POLAR;
                }

                String[] recordValues = record.substring(2).split("/");
                if (currentType != null && recordValues.length > 0) {
                    try {
                        recordsMap.put(currentType, new RIRecordPart(
                                recordValues[0] != null ? Float.parseFloat(recordValues[0]) : null,
                                recordValues.length > 2 && recordValues[1] != null && recordValues[2] != null ? Integer.parseInt(recordValues[1]) : null,
                                recordValues.length > 2 && recordValues[1] != null && recordValues[2] != null ? Float.parseFloat(recordValues[2]) : null));
                    } catch (Exception e) {
                        logger.warning("Failed to parse RI record: " + line);
                    }
                }

            }





        }
        return recordsMap;
    }

    public Float getRI(RIColumn type) {
        if (map.containsKey(type)) {
            return map.get(type).ri();
        }
        else if (map.containsKey(RIColumn.DEFAULT)) {
            return map.get(RIColumn.DEFAULT).ri();
        }
        else {
            return null;
        }
    }

    protected record RIRecordPart(Float ri, Integer count, Float ci) {
    }

}


