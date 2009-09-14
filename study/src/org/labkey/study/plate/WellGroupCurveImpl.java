/*
 * Copyright (c) 2006-2009 LabKey Corporation
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

import java.util.ArrayList;
import java.util.List;

/**
 * User: brittp
 * Date: Oct 25, 2006
 * Time: 4:45:19 PM
 */
public abstract class WellGroupCurveImpl implements DilutionCurve
{
    protected static final int CURVE_SEGMENT_COUNT = 100;

    protected WellGroup _wellGroup;
    protected DoublePoint[] _curve = null;
    protected boolean assumeDecreasing;
    protected Double _fitError;
    protected WellSummary[] _wellSummaries = null;
    protected Double _auc;

    public WellGroupCurveImpl(WellGroup wellGroup, boolean assumeDecreasing, PercentCalculator percentCalculator) throws FitFailedException
    {
        _wellGroup = wellGroup;
        this.assumeDecreasing = assumeDecreasing;

        List<WellData> data = getWellData();
        _wellSummaries = new WellSummary[data.size()];
        for (int i = 0; i < data.size(); i++)
        {
            WellData well = data.get(i);
            _wellSummaries[i] = new WellSummary(percentCalculator.getPercent(_wellGroup, data.get(i)), well.getDilution());
        }
    }

    public double getMaxPercentage() throws FitFailedException
    {
        DoublePoint[] curve = getCurve();
        double max = curve[0].getY();
        for (int i = 1; i < CURVE_SEGMENT_COUNT; i++)
        {
            double percent = curve[i].getY();
            if (percent > max)
                max = percent;
        }
        return max;
    }

    public double getMinPercentage() throws FitFailedException
    {
        DoublePoint[] curve = getCurve();
        double min = curve[0].getY();
        for (int i = 1; i < CURVE_SEGMENT_COUNT; i++)
        {
            double percent = curve[i].getY();
            if (percent < min)
                min = percent;
        }
        return min;
    }

    public double getMaxDilution()
    {
        List<WellData> datas = getWellData();
        double max = datas.get(0).getDilution();
        for (WellData data : datas)
        {
            if (data.getDilution() > max)
                max = data.getDilution();
        }
        return max;
    }

    public double getMinDilution()
    {
        List<WellData> datas = getWellData();
        double min = datas.get(0).getDilution();
        for (WellData data : datas)
        {
            if (data.getDilution() < min)
                min = data.getDilution();
        }
        return min;
    }

    protected List<WellData> getWellData()
    {
        return _wellGroup.getWellData(true);
    }

    protected class WellSummary
    {
        private double _neutralization;
        private double _dilution;

        public WellSummary(double neutralization, double dilution)
        {
            _dilution = dilution;
            _neutralization = neutralization;
        }

        public double getNeutralization()
        {
            return _neutralization;
        }

        public double getDilution()
        {
            return _dilution;
        }
    }

    public double getInterpolatedCutoffDilution(double cutoff)
    {
        boolean dataAbove = false;
        List<Double> possibleMatches = new ArrayList<Double>();
        for (int i = 1; i < _wellSummaries.length; i++)
        {
            double high = _wellSummaries[i - 1].getNeutralization();
            double low = _wellSummaries[i].getNeutralization();
            boolean reverseCurve = false;
            if (high < low)
            {
                double temp = low;
                low = high;
                high = temp;
                reverseCurve = true;
            }
            if (high >= cutoff && low <= cutoff)
            {
                double logDilHigh = Math.log10(_wellSummaries[i - 1].getDilution());
                double logDilLow = Math.log10(_wellSummaries[i].getDilution());
                if (reverseCurve)
                {
                    double temp = logDilHigh;
                    logDilHigh = logDilLow;
                    logDilLow = temp;
                }
                if (low != high)
                    possibleMatches.add(Math.pow(10, logDilLow - (low - cutoff) * (logDilLow - logDilHigh) / (low - high)));
                else
                    possibleMatches.add((logDilLow + logDilHigh) / 2);
            }
            else if (low > cutoff)
                dataAbove = true;
        }
        if (possibleMatches.size() > 0)
        {
            double total = 0;
            for (Double d : possibleMatches)
                total += d;
            return total / possibleMatches.size();
        }
        return getOutOfRangeValue(dataAbove);
    }

    public double getCutoffDilution(double cutoff) throws FitFailedException
    {
        DoublePoint[] curve = getCurve();

        // convert from decimal to percent, to match our curve values:
        cutoff *= 100;
        if (cutoff > getMaxPercentage())
            return getOutOfRangeValue(false);

        for (int i = 1; i < CURVE_SEGMENT_COUNT; i++)
        {
            double highPercent = curve[i - 1].getY();
            double lowPercent = curve[i].getY();
            if (highPercent < lowPercent)
            {
                double temp = highPercent;
                highPercent = lowPercent;
                lowPercent = temp;
            }
            if (highPercent >= cutoff && lowPercent <= cutoff)
            {
                double logDilHigh = Math.log10(curve[i - 1].getX());
                double logDilLow = Math.log10(curve[i].getX());
                if (logDilHigh < logDilLow)
                {
                    double temp = logDilHigh;
                    logDilHigh = logDilLow;
                    logDilLow = temp;
                }
                return Math.pow(10, logDilLow - (lowPercent - cutoff) * (logDilLow - logDilHigh) / (lowPercent - highPercent));
            }
        }

        return getOutOfRangeValue(true);
    }

    private double getOutOfRangeValue(boolean alwaysAboveCutoff)
    {
        if (alwaysAboveCutoff)
        {
            if (assumeDecreasing)
                return Double.POSITIVE_INFINITY;
            else
                return Double.NEGATIVE_INFINITY;
        }
        else
        {
            if (assumeDecreasing)
                return Double.NEGATIVE_INFINITY;
            else
                return Double.POSITIVE_INFINITY;
        }
    }

    protected void ensureCurve() throws FitFailedException
    {
        getCurve();
    }

    public DoublePoint[] getCurve() throws FitFailedException
    {
        if (_curve == null)
            _curve = renderCurve();
        return _curve;
    }

    protected abstract DoublePoint[] renderCurve() throws FitFailedException;

    public abstract Parameters getParameters() throws FitFailedException;

    public double getFitError() throws FitFailedException
    {
        ensureCurve();
        return _fitError;
    }

    /**
     * Calculate the area under the curve
     */
    public double calculateAUC() throws FitFailedException
    {
        if (_auc == null)
        {
            double min = _wellSummaries[0].getDilution();
            double max = _wellSummaries[_wellSummaries.length-1].getDilution();

            _auc = (1/(Math.log10(max) - Math.log10(min))) * integrate(min, max, 0.00001, 10) / 100.0;
        }
        return _auc;
    }

    /**
     * Approximate the integral of the curve using an adaptive simpsons rule.
     *
     * @param a lower bounds to integrate over
     * @param b upper bounds to integrate over
     * @param epsilon the error tolerance
     * @param maxRecursionDepth the maximum depth the algorithm will recurse to
     *
     * @throws FitFailedException
     */
    private double integrate(double a, double b, double epsilon, int maxRecursionDepth) throws FitFailedException
    {
        Parameters parameters = getParameters();

        double c = (a + b)/2;
        double h = Math.log10(b) - Math.log10(a);
        double fa = fitCurve(a, parameters);
        double fb = fitCurve(b, parameters);
        double fc = fitCurve(c, parameters);
        double s = (h/6)*(fa + 4*fc + fb);

        return _integrate(a, b, epsilon, s, fa, fb, fc, maxRecursionDepth, parameters);
    }

    private double _integrate(double a, double b, double epsilon, double s,
                              double fa, double fb, double fc, int bottom, Parameters parameters) throws FitFailedException
    {
        double c = (a + b)/2;
        double h = Math.log10(b) - Math.log10(a);
        double d = (a + c)/2;
        double e = (c + b)/2;
        double fd = fitCurve(d, parameters);
        double fe = fitCurve(e, parameters);
        double sLeft = (h/12)*(fa + 4*fd + fc);
        double sRight = (h/12)*(fc + 4*fe + fb);
        double s2 = sLeft + sRight;

        if (bottom <= 0 || Math.abs(s2 - s) <= 15*epsilon)
            return s2 + (s2 - s)/15;
        return _integrate(a, c, epsilon/2, sLeft,  fa, fc, fd, bottom-1, parameters) +
                _integrate(c, b, epsilon/2, sRight, fc, fb, fe, bottom-1, parameters);
    }

    protected double calculateFitError(Parameters parameters)
    {
        double deviationValue = 0;
        for (WellSummary well : _wellSummaries)
        {
            double dilution = well.getDilution();
            double expectedPercentage = 100 * well.getNeutralization();
            double foundPercentage = fitCurve(dilution, parameters);
            deviationValue += Math.pow(foundPercentage - expectedPercentage, 2);
        }
        return Math.sqrt(deviationValue / _wellSummaries.length);
    }
}
