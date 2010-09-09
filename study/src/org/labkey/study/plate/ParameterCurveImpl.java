/*
 * Copyright (c) 2009-2010 LabKey Corporation
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

package org.labkey.study.plate;

import org.labkey.api.study.DilutionCurve;
import org.labkey.api.study.WellData;
import org.labkey.api.study.WellGroup;

import java.util.*;

/**
 * Created by IntelliJ IDEA.
 * User: klum
 * Date: Sep 2, 2009
 */
public abstract class ParameterCurveImpl extends WellGroupCurveImpl
{
    private FitParameters _fitParameters;
    private FitType _fitType;

    public interface SigmoidalParameters extends Parameters
    {
        double getAsymmetry();

        double getInflection();

        double getSlope();

        double getMax();

        double getMin();
    }

    private static class FitParameters implements Cloneable, SigmoidalParameters
    {
        public Double fitError;
        public double asymmetry;
        public double EC50;
        public double slope;
        public double max;
        public double min;

        public FitParameters copy()
        {
            try
            {
                return (FitParameters) super.clone();
            }
            catch (CloneNotSupportedException e)
            {
                throw new RuntimeException(e);
            }
        }

        public Double getFitError()
        {
            return fitError;
        }

        public double getAsymmetry()
        {
            return asymmetry;
        }

        public double getInflection()
        {
            return EC50;
        }

        public double getSlope()
        {
            return slope;
        }

        public double getMax()
        {
            return max;
        }

        public double getMin()
        {
            return min;
        }

        public Map<String, Object> toMap()
        {
            Map<String, Object> params = new HashMap<String, Object>();
            params.put("asymmetry", getAsymmetry());
            params.put("inflection", getInflection());
            params.put("slope", getSlope());
            params.put("max", getMax());
            params.put("min", getMin());

            return Collections.unmodifiableMap(params);
        }
    }

    public ParameterCurveImpl(List<WellGroup> wellGroups, boolean assumeDecreasing, PercentCalculator percentCalculator, DilutionCurve.FitType fitType) throws FitFailedException
    {
        super(wellGroups, assumeDecreasing, percentCalculator);
        _fitType = fitType;
    }

    protected DoublePoint[] renderCurve() throws FitFailedException
    {
        Map<WellData, WellGroup> wellDatas = getWellData();
        List<Double> percentages = new ArrayList<Double>(wellDatas.size());
        for (WellSummary well : _wellSummaries)
        {
            double percentage = 100 * well.getNeutralization();
            percentages.add(percentage);
        }
        Collections.sort(percentages);

        // use relative percentage rather than fixed 50% value.  Divide by 2*100 to average
        // and convert back to 0.0-1.0 percentage form:
        double minPercentage = percentages.get(0);
        double maxPercentage = percentages.get(percentages.size() - 1);

        _fitParameters = calculateFitParameters(minPercentage, maxPercentage);
        _fitError = _fitParameters.fitError;
        DoublePoint[] curveData = new DoublePoint[CURVE_SEGMENT_COUNT];
        double logX = Math.log10(getMinDilution());
        double logInterval = (Math.log10(getMaxDilution()) - logX) / (CURVE_SEGMENT_COUNT - 1);
        for (int i = 0; i < CURVE_SEGMENT_COUNT; i++)
        {
            double x = Math.pow(10, logX);
            double y = fitCurve(x, _fitParameters);
            curveData[i] = new DoublePoint(x, y);
            logX += logInterval;
        }
        return curveData;
    }

    public double fitCurve(double x, Parameters params)
    {
        if (params instanceof SigmoidalParameters)
        {
            SigmoidalParameters parameters = (SigmoidalParameters)params;
            return parameters.getMin() + ((parameters.getMax() - parameters.getMin()) /
                    Math.pow(1 + Math.pow(10, (Math.log10(parameters.getInflection()) - Math.log10(x)) * parameters.getSlope()), parameters.getAsymmetry()));
        }
        throw new IllegalArgumentException("params is not an instance of SigmoidalParameters");
    }

    private FitParameters calculateFitParameters(double minPercentage, double maxPercentage) throws FitFailedException
    {
        FitParameters bestFit = null;
        FitParameters parameters = new FitParameters();
        double step = 10;
        if (_fitType == FitType.FOUR_PARAMETER)
            parameters.asymmetry = 1;
        // try reasonable variants of max and min, in case there's a better fit.  We'll keep going past "reasonable" if
        // we haven't found a single bestFit option, but we need to bail out at some point.  We currently quit once max
        // reaches 200 or min reaches -100, since these values don't seem biologically reasonable.
        for (double min = minPercentage; (bestFit == null || min > 0 - step) && min > (minPercentage - 100); min -= step )
        {
            parameters.min = min;
            for (double max = maxPercentage; (bestFit == null || max <= 100 + step) && max < (maxPercentage + 100); max += step )
            {
                double absoluteCutoff = min + (0.5 * (max - min));
                double relativeEC50 = getInterpolatedCutoffDilution(absoluteCutoff/100);
                if (relativeEC50 != Double.POSITIVE_INFINITY && relativeEC50 != Double.NEGATIVE_INFINITY)
                {
                    parameters.max = max;
                    parameters.EC50 = relativeEC50;
                    for (double slopeRadians = 0; slopeRadians < Math.PI; slopeRadians += Math.PI / 30)
                    {
                        parameters.slope = Math.tan(slopeRadians);
                        switch (_fitType)
                        {
                            case FIVE_PARAMETER:
                                for (double asymmetryFactor = 0; asymmetryFactor < Math.PI; asymmetryFactor += Math.PI / 30)
                                {
                                    parameters.asymmetry = asymmetryFactor;
                                    parameters.fitError = calculateFitError(parameters);
                                    if (bestFit == null || parameters.fitError < bestFit.fitError)
                                        bestFit = parameters.copy();
                                }
                                break;
                            case FOUR_PARAMETER:
                                parameters.asymmetry = 1;
                                parameters.fitError = calculateFitError(parameters);
                                if (bestFit == null || parameters.fitError < bestFit.fitError)
                                    bestFit = parameters.copy();
                                break;
                        }
                    }
                }
            }
        }
        if (bestFit == null)
        {
            throw new FitFailedException("Unable to find any parameters to fit a curve to wellgroup " + _wellGroups.get(0).getName() +
                    ".  Your plate template may be invalid.  Please contact an administrator to report this problem.  " +
                    "Debug info: minPercentage = " + minPercentage + ", maxPercentage = " + maxPercentage + ", fitType = " +
                    _fitType.getLabel() + ", num data points = " + _wellSummaries.length);
        }
        return bestFit;
    }

    public Parameters getParameters() throws FitFailedException
    {
        ensureCurve();
        return _fitParameters;
    }

    public static class FourParameterCurve extends ParameterCurveImpl
    {
        public FourParameterCurve(List<WellGroup> wellGroups, boolean assumeDecreasing, PercentCalculator percentCalculator) throws FitFailedException
        {
            super(wellGroups, assumeDecreasing, percentCalculator, FitType.FOUR_PARAMETER);
        }
    }

    public static class FiveParameterCurve extends ParameterCurveImpl
    {
        public FiveParameterCurve(List<WellGroup> wellGroups, boolean assumeDecreasing, PercentCalculator percentCalculator) throws FitFailedException
        {
            super(wellGroups, assumeDecreasing, percentCalculator, FitType.FIVE_PARAMETER);
        }
    }
}
