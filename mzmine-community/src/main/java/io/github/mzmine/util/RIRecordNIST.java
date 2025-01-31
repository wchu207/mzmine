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

import java.util.Map;
import java.util.logging.Logger;

import static java.util.Map.entry;

// This holds information from the RI field in NIST libraries for gas chromatography
//   RI field is of form "s=RI/n_samples/CI n=RI/n_samples/CI p=RI/n_samples/CI"
//   s denotes semipolar, n denotes nonpolar, and p denotes polar
//   If there are 0 samples for s or n or p, then that part is skipped
//   If there is one sample, then n_samples and CI are skipped
public record RIRecordNIST(
        Float s_ri, Integer s_count, Float s_ci,
        Float n_ri, Integer n_count, Float n_ci,
        Float p_ri, Integer p_count, Float p_ci
) {
    private static final Logger logger = Logger.getLogger(RIRecordNIST.class.getName());

    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(s_ri != null ? String.format("s=%d", Math.round(s_ri)) : "");
        sb.append((s_count != null && s_ci != null) ? String.format("/%d/%d", s_count, Math.round(s_ci)) : "");

        sb.append(s_ri != null && n_ri != null ? " " : "");
        sb.append(n_ri != null ? String.format("n=%d", Math.round(n_ri)) : "");
        sb.append((n_count != null && n_ci != null) ? String.format("/%d/%d", n_count, Math.round(n_ci)) : "");

        sb.append((s_ri != null || n_ri != null) && p_ri != null ? " " : "");
        sb.append(p_ri != null ? String.format("p=%d", Math.round(p_ri)) : "");
        sb.append((p_count != null && p_ci != null) ? String.format("/%d/%d", p_count, Math.round(p_ci)) : "");

        return sb.toString();
    }

    public static RIRecordNIST parse(String line) {
        String[] records = line.split("\\s+");

        Map<String, Object[]> recordsMap = Map.ofEntries(
                entry("s", new Object[] {null, null, null}),
                entry("n", new Object[] {null, null, null}),
                entry("p", new Object[] {null, null, null})
        );

        for(String record : records) {
            String currentType = null;
            if (record.startsWith("s=")) {
                currentType = "s";
            } else if (record.startsWith("n=")) {
                currentType = "n";
            } else if (record.startsWith("p=")) {
                currentType = "p";
            }

            if (currentType != null) {
                String[] recordValues = record.substring(2).split("/");
                try {
                    if (recordValues[0] != null) {
                        recordsMap.get(currentType)[0] = Float.parseFloat(recordValues[0]);
                    }
                    if (recordValues[1] != null && recordValues[2] != null) {
                        recordsMap.get(currentType)[1] = Integer.parseInt(recordValues[1]);
                        recordsMap.get(currentType)[2] = Float.parseFloat(recordValues[2]);
                    }
                } catch (Exception e) {
                    logger.warning("Failed to parse NIST RI record: " + line);
                }
            }
        }
        return new RIRecordNIST(
                (Float) recordsMap.get("s")[0], (Integer) recordsMap.get("s")[1], (Float) recordsMap.get("s")[2],
                (Float) recordsMap.get("n")[0], (Integer) recordsMap.get("n")[1], (Float) recordsMap.get("n")[2],
                (Float) recordsMap.get("p")[0], (Integer) recordsMap.get("p")[1], (Float) recordsMap.get("p")[2]
        );
    }

}
