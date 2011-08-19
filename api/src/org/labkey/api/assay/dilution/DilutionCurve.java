/*
 * Copyright (c) 2006-2010 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.labkey.api.assay.dilution;

import org.labkey.api.study.WellData;
import org.labkey.api.study.WellGroup;
import org.labkey.api.util.Pair;

import java.util.Map;

/**
 * User: brittp
 * Date: Oct 25, 2006
 * Time: 4:58:12 PM
 */
public interface DilutionCurve
{
    public interface Parameters
    {
        /**
         * Returns a map representation of the parameters, used for
         * serialization of parameter information in the Client API
         */
        public Map<String, Object> toMap();
    }

    public class FitFailedException extends Exception
    {
        public FitFailedException(String message)
        {
            super(message);
        }
    }

    public enum FitType
    {
        FOUR_PARAMETER("Four Parameter", "4pl"),
        FIVE_PARAMETER("Five Parameter", "5pl"),
        POLYNOMIAL("Polynomial", "poly");

        private String _label;
        private String _colSuffix;

        private FitType(String label, String colSuffix)
        {
            _label = label;
            _colSuffix = colSuffix;
        }

        public String getColSuffix()
        {
            return _colSuffix;
        }

        public String getLabel()
        {
            return _label;
        }

        public static FitType fromLabel(String label)
        {
            for (FitType type : values())
            {
                if (type.getLabel().equals(label))
                    return type;
            }
            return null;
        }

        public static FitType fromColSuffix(String suffix)
        {
            for (FitType type : values())
            {
                if (type.getColSuffix().equals(suffix))
                    return type;
            }
            return null;
        }
    }

    DoublePoint[] getCurve() throws FitFailedException;

    Parameters getParameters() throws FitFailedException;

    double getFitError() throws FitFailedException;

    double getCutoffDilution(double percent) throws FitFailedException;

    double getInterpolatedCutoffDilution(double percent);

    double getMinDilution() throws FitFailedException;

    double getMaxDilution() throws FitFailedException;

    double fitCurve(double x, Parameters curveParameters);

    double calculateAUC(AUCType type) throws FitFailedException;

    public static class DoublePoint extends Pair<Double, Double>
    {
        public DoublePoint(double x, double y)
        {
            super(x, y);
        }

        public double getX()
        {
            return getKey();
        }

        public double getY()
        {
            return getValue();
        }
    }

    public enum AUCType
    {
        NORMAL("Normal"),
        POSITIVE("Positive"),
        NEGATIVE("Negative");

        private String _label;

        private AUCType(String label)
        {
            _label = label;
        }
    }

    public static interface PercentCalculator
    {
        double getPercent(WellGroup group, WellData data) throws FitFailedException;
    }
}
