/*
 * Copyright (c) 2008-2013 LabKey Corporation
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

package org.labkey.api.reports.report.r;

import org.apache.log4j.Logger;
import org.labkey.api.gwt.client.util.StringUtils;
import org.labkey.api.reports.Report;
import org.labkey.api.reports.report.ScriptOutput;
import org.labkey.api.reports.report.r.view.HrefOutput;
import org.labkey.api.util.PageFlowUtil;

import java.io.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * User: Karl Lum
 * Date: May 5, 2008
 */
public class ParamReplacementSvc
{
    private static final Logger _log = Logger.getLogger(ParamReplacementSvc.class);
    private static final ParamReplacementSvc _instance = new ParamReplacementSvc();
    private static final Map<String, String> _outputSubstitutions = new HashMap<>();

    // the default param replacement pattern : ${}
    public static final String REPLACEMENT_PARAM = "\\$\\{(.*?)\\}";
    public static final String REPLACEMENT_PARAM_ESC = "\\$%7[bB](.*?)%7[dD]";
    public static final Pattern defaultScriptPattern = Pattern.compile(REPLACEMENT_PARAM);

    private ParamReplacementSvc(){}

    public static ParamReplacementSvc get()
    {
        return _instance;
    }

    public void registerHandler(ParamReplacement rout)
    {
        if (_outputSubstitutions.containsKey(rout.getId()))
            throw new IllegalStateException("Replacement parameter type: " + rout.getId() + " has previously been registered");

        if (!rout.getId().endsWith(":"))
            throw new IllegalArgumentException("The ID of a replacement parameter must end with a ':'");

        _outputSubstitutions.put(rout.getId(), rout.getClass().getName());
    }

    /**
     * Returns a ParamReplacement from a replacement parameter id
     */
    public ParamReplacement getHandlerInstance(String id)
    {
        if (_outputSubstitutions.containsKey(id))
        {
            try {
                String className = _outputSubstitutions.get(id);
                return (ParamReplacement)Class.forName(className).newInstance();
            }
            catch (Exception e)
            {
                _log.error("Unable to create report output handler", e);
            }
        }
        return null;
    }

    /**
     * Returns a ParamReplacement from a replacement parameter of the form: <id><name>
     */
    public ParamReplacement getHandler(String token)
    {
        return fromToken(token);
    }

    /**
     * Finds all the replacement parameters for a given script block
     */
    public List<ParamReplacement> getParamReplacements(String script)
    {
        return getParamReplacements(script, defaultScriptPattern);
    }

    /**
     * Finds all the replacement parameters for a given script block
     *
     * @param pattern - the regular expression pattern for the replacements
     */
    public List<ParamReplacement> getParamReplacements(String script, Pattern pattern)
    {
        List<ParamReplacement> params = new ArrayList<>();
        if (script != null)
        {
            Matcher m = pattern.matcher(script);

            while (m.find())
            {
                ParamReplacement param = fromToken(m.group(1));
                if (param != null)
                    params.add(param);
            }
        }
        return params;
    }

    private ParamReplacement fromToken(String value)
    {
        int idx = value.indexOf(':');
        if (idx != -1)
        {
            String id = value.substring(0, idx+1);
            String name = value.substring(idx+1);

            ParamReplacement param = getHandlerInstance(id);
            if (param != null)
            {
                if (name.indexOf('?') != -1)
                {
                    String[] parts = name.split("\\?");
                    if (parts.length == 2)
                    {
                        param.setName(parts[0]);
                        param.setProperties(PageFlowUtil.mapFromQueryString(parts[1]));
                    }
                }
                else
                    param.setName(name);
                return param;
            }
        }
        return null;
    }

    /**
     * Replaces an input replacement symbol with the full path name of the specified input file.
     */
    public String processInputReplacement(String script, String replacementParam, String value) throws Exception
    {
        return processInputReplacement(script, replacementParam, value, defaultScriptPattern);
    }

    /**
     * Replaces an input replacement symbol with the full path name of the specified input file.
     *
     * @param pattern - the regular expression pattern for the replacements
     */
    public String processInputReplacement(String script, String replacementParam, String replacementValue, Pattern pattern) throws Exception
    {
        Matcher m = pattern.matcher(script);
        StringBuffer sb = new StringBuffer();

        while (m.find())
        {
            String value = m.group(1);
            if (replacementParam.equals(value))
            {
                m.appendReplacement(sb, replacementValue);
            }
        }
        m.appendTail(sb);
        return sb.toString();
    }

    /**
     * Removes any replacement param sequences '${}' from the specified String, this is useful when a replacement
     * parameter is conditional and you need to remove any unprocessed sequences before the command is executed
     */
    public String clearUnusedReplacements(String script) throws Exception
    {
        return script.replaceAll(REPLACEMENT_PARAM, "");
    }
            
    /**
     * Finds and processes all replacement parameters for a given script block. The
     * returned string will have all valid replacement references converted.
     *
     * @param parentDirectory - the parent directory to create the output files for each param replacement.
     * @param outputReplacements - the list of processed replacements found in the source script.
     */
    public String processParamReplacement(String script, File parentDirectory, String remoteParentDirectoryPath, List<ParamReplacement> outputReplacements) throws Exception
    {
        return processParamReplacement(script, parentDirectory, remoteParentDirectoryPath, outputReplacements, defaultScriptPattern);
    }

    /**
     * Finds and processes all replacement parameters for a given script block. The
     * returned string will have all valid replacement references converted.
     *
     * @param parentDirectory - the parent directory to create the output files for each param replacement.
     * @param remoteParentDirectoryPath - the remote reference to this path if specified; may be null
     * @param outputReplacements - the list of processed replacements found in the source script.
     */
    public String processParamReplacement(String script, File parentDirectory, String remoteParentDirectoryPath, List<ParamReplacement> outputReplacements, Pattern pattern) throws Exception
    {
        Matcher m = pattern.matcher(script);
        StringBuffer sb = new StringBuffer();

        while (m.find())
        {
            ParamReplacement param = fromToken(m.group(1));
            if (param != null)
            {
                File resultFile = param.convertSubstitution(parentDirectory);
                String resultFileName = null;

                if (!StringUtils.isEmpty(remoteParentDirectoryPath))
                {
                    //
                    // now that we've created the resultFile locally, replace the parameter with the remote
                    // machines view of it
                    //
                    resultFileName = remoteParentDirectoryPath + "/" + resultFile.getName();
                }
                else
                {
                    resultFileName = resultFile.getAbsolutePath().replaceAll("\\\\", "/");
                }
                outputReplacements.add(param);
                m.appendReplacement(sb, resultFileName);
            }
        }
        m.appendTail(sb);

        return sb.toString();
    }

    /**
     * Finds and processes all replacement parameters for a given script block. The
     * returned string will have all valid replacement references converted.  Note that for this overload
     * the files have already been created and we are replacing with a valid URL inline here.
     *
     * @param script - the script upon which to replace the Href parameters
     * @param parentDirectory - the parent directory to create the output files for each param replacement.
     * @param pattern - the remote reference to this path if specified; may be null
     */
    public String processHrefParamReplacement(Report report, String script, File parentDirectory, Pattern pattern) throws Exception
    {
        Matcher m = pattern.matcher(script);
        StringBuffer sb = new StringBuffer();

        while (m.find())
        {
            ParamReplacement param = fromToken(m.group(1));
            if (param != null && HrefOutput.class.isInstance(param))
            {
                HrefOutput href = (HrefOutput) param;
                href.setReport(report);
                href.setFile(new File(parentDirectory, href.getName()));
                ScriptOutput o = href.renderAsScriptOutput();
                if (null != o)
                    m.appendReplacement(sb, o.getValue());
            }
        }
        m.appendTail(sb);

        return sb.toString();
    }

    public void toFile(List<ParamReplacement> outputSubst, File file) throws Exception
     {
         BufferedWriter bw = null;
         try {
             bw = new BufferedWriter(new FileWriter(file));
             for (ParamReplacement output : outputSubst)
             {
                 if (output.getName() != null && output.getFile() != null)
                    bw.write(output.getId() + '\t' + output.getName() + '\t' + output.getFile().getAbsolutePath() + '\t' +
                            PageFlowUtil.toQueryString(output.getProperties().entrySet()) + '\n');
             }
         }
         finally
         {
             if (bw != null)
                 try {bw.close();} catch (IOException ioe) {}
         }
     }

     public List<ParamReplacement> fromFile(File file) throws Exception
     {
         BufferedReader br = null;
         List<ParamReplacement> outputSubst = new ArrayList();

         try {
             if (file.exists())
             {
                 br = new BufferedReader(new FileReader(file));
                 String l;
                 while ((l = br.readLine()) != null)
                 {
                     String[] parts = l.split("\\t");
                     if (parts.length == 4)
                     {
                         ParamReplacement handler = getHandlerInstance(parts[0]);
                         if (handler != null)
                         {
                             handler.setName(parts[1]);
                             handler.setFile(new File(parts[2]));
                             handler.setProperties(PageFlowUtil.mapFromQueryString(parts[3]));

                             outputSubst.add(handler);
                         }
                     }
                 }
             }
         }
         finally
         {
             if (br != null)
                 try {br.close();} catch(IOException ioe) {}
         }
         return outputSubst;
     }
}
