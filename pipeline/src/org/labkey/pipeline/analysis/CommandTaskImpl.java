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
package org.labkey.pipeline.analysis;

import org.junit.Assert;
import org.apache.commons.lang3.StringUtils;
import org.jmock.Mockery;
import org.jmock.lib.legacy.ClassImposteriser;
import org.junit.Test;
import org.labkey.api.collections.RowMapFactory;
import org.labkey.api.data.TSVMapWriter;
import org.labkey.api.module.Module;
import org.labkey.api.pipeline.*;
import org.labkey.api.pipeline.cmd.CommandTask;
import org.labkey.api.pipeline.cmd.*;
import org.labkey.api.pipeline.file.FileAnalysisJobSupport;
import org.labkey.api.resource.FileResource;
import org.labkey.api.resource.Resource;
import org.labkey.api.util.FileType;
import org.labkey.api.util.NetworkDrive;
import org.labkey.api.util.Path;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * <code>CommandTask</code>
 *
 * @author brendanx
 */
public class CommandTaskImpl extends WorkDirectoryTask<CommandTaskImpl.Factory> implements CommandTask
{
    public static class Factory extends AbstractTaskFactory<CommandTaskFactorySettings, Factory>
    {
        private String _statusName = "COMMAND";
        private String _protocolActionName;
        private Map<String, String> _environment = new HashMap<>();
        private Map<String, TaskPath> _inputPaths = new HashMap<>();
        private Map<String, TaskPath> _outputPaths = new HashMap<>();
        private ListToCommandArgs _converter = new ListToCommandArgs();
        private boolean _copyInput;
        private boolean _removeInput;
        private boolean _pipeToOutput;
        private int _pipeOutputLineInterval;
        private boolean _preview;
        private String _actionableInput;
        private String _installPath;
        private Path _moduleTaskPath;

        public Factory()
        {
            super(new TaskId(CommandTask.class));
        }

        public Factory(String name)
        {
            super(new TaskId(CommandTask.class, name));
        }

        public Factory(TaskId taskId)
        {
            super(taskId);
        }

        protected void configure(CommandTaskFactorySettings settings)
        {
            super.configure(settings);

            if (settings.getStatusName() != null)
                _statusName = settings.getStatusName();

            if (settings.getProtocolActionName() != null)
                _protocolActionName = settings.getProtocolActionName();

            if (settings.getInputPaths() != null && settings.getInputPaths().size() > 0)
                _inputPaths = settings.getInputPaths();

            if (settings.getOutputPaths() != null && settings.getOutputPaths().size() > 0)
                _outputPaths = settings.getOutputPaths();

            if (settings.getEnvironment() != null && !settings.getEnvironment().isEmpty())
                _environment = settings.getEnvironment();

            if (settings.getConverter() != null && settings.getConverter().getConverters() != null)
                _converter = settings.getConverter();    

            if (settings.isCopyInputSet())
                _copyInput = settings.isCopyInput();

            if (settings.isRemoveInputSet())
                _removeInput = settings.isRemoveInput();

            if (settings.isPipeToOutputSet())
                _pipeToOutput = settings.isPipeToOutput();

            if (settings.isPipeOutputLineIntervalSet())
                _pipeOutputLineInterval = settings.getPipeOutputLineInterval();

            if (settings.isPreviewSet())
                _preview = settings.isPreview();

            if (settings.getActionableInput() != null)
                _actionableInput = settings.getActionableInput();

            if (settings.getInstallPath() != null)
                _installPath = settings.getInstallPath();
        }

        public CommandTaskImpl createTask(PipelineJob job)
        {
            return new CommandTaskImpl(job, this);
        }

        protected String getProtocolActionName()
        {
            if (_protocolActionName == null)
            {
                return getId().getName();
            }
            else
            {
                return _protocolActionName;
            }
        }

        public List<String> getProtocolActionNames()
        {
            return Collections.singletonList(getProtocolActionName());
        }

        public String getStatusName()
        {
            return _statusName;
        }

        protected void setStatusName(String statusName)
        {
            _statusName = statusName;
        }

        public String getInstallPath()
        {
            return _installPath;
        }

        public boolean isJobComplete(PipelineJob job)
        {
            // TODO: Safer way to do this.
            FileAnalysisJobSupport support = (FileAnalysisJobSupport) job;
            int outputCount = 0;
            boolean hasOptional = false;
            for (TaskPath tp : getOutputPaths().values())
            {
                // TODO: Join somehow with code in CommandTaskImpl
                FileType type = tp.getType();

                // TODO: do we really want to only check for files of default extension type?
                //       possibly author did not realize that type.getName single-arg overload used here does that
                //       (bpratt renamed it to getDefaultName to make its function more obvious)
                String fileName = type != null ? type.getDefaultName(support.getBaseName()) : tp.getName();

                File result;
                // Check if the output is specifically flagged to go into the analysis directory so we check in the right
                // place when deciding if the task has already been performed
                if (tp.isForceToAnalysisDir())
                {
                    result = new File(support.getAnalysisDirectory(), fileName);
                }
                else
                {
                    result = support.findOutputFile(fileName);
                }
                if (tp.isOptional())
                {
                    hasOptional = true;
                }
                else
                {
                    if(!result.exists())
                    {
                        return false;
                    }
                }
                if (result.exists())
                {
                    outputCount++;
                }
            }

            if (hasOptional)
            {
                return outputCount > 0;
            }

            // If there were no output paths, then the command is run for some
            // other reason than its outputs.
            return (getOutputPaths().size() > 0);
        }

        public boolean isParticipant(PipelineJob job) throws IOException
        {
            if (!super.isParticipant(job))
            {
                return false;
            }
            
            // The first converter is responsible for the command name.
            List<TaskToCommandArgs> converters = getConverters();
            assert converters != null && converters.size() > 0 :
                    "No converters found in " + getId();
            TaskToCommandArgs commandNameConverter = converters.get(0);

            CommandTaskImpl task = createTask(job);
            // Need to set up the work directory so that it can figure out what arguments will be used in the call
            WorkDirectory wd = createWorkDirectory(job.getJobGUID(), job.getJobSupport(FileAnalysisJobSupport.class), job.getLogger());
            task.setWorkDirectory(wd);
            try
            {
                // If it produces nothing for the command line, then this command should not be executed.
                return commandNameConverter.toArgs(task, new HashSet<TaskToCommandArgs>()).length != 0;
            }
            finally
            {
                wd.remove(true);
                task.setWorkDirectory(null);
            }
        }

        public List<FileType> getInputTypes()
        {
            if (_actionableInput == null)
            {
                // If the config doesn't specify that only one of the inputs should have a button next to it,
                // use them all
                List<FileType> result = new ArrayList<>(_inputPaths.size());
                for (TaskPath taskPath : _inputPaths.values())
                {
                    FileType ft = taskPath.getType();
                    if (ft != null)
                        result.add(ft);
                }
                return result;
            }
            TaskPath tp = _inputPaths.get(_actionableInput);
            return (tp == null ? null : Collections.singletonList(tp.getType()));
        }

        public Map<String, TaskPath> getInputPaths()
        {
            return _inputPaths;
        }

        protected void setInputPaths(Map<String, TaskPath> inputPaths)
        {
            _inputPaths = inputPaths;
        }

        public Map<String, TaskPath> getOutputPaths()
        {
            return _outputPaths;
        }

        protected void setOututPaths(Map<String, TaskPath> outputPaths)
        {
            _outputPaths = outputPaths;
        }

        public List<TaskToCommandArgs> getConverters()
        {
            return _converter.getConverters();
        }

        protected void setConverter(ListToCommandArgs converter)
        {
            _converter = converter;
        }

        public String[] toArgs(CommandTask task) throws IOException
        {
            return _converter.toArgs(task, new HashSet<TaskToCommandArgs>());
        }

        public boolean isCopyInput()
        {
            return _copyInput;
        }

        public boolean isRemoveInput()
        {
            return _removeInput;
        }

        public boolean isPipeToOutput()
        {
            return _pipeToOutput;
        }

        public int getPipeOutputLineInterval()
        {
            return _pipeOutputLineInterval;
        }

        public boolean isPreview()
        {
            return _preview;
        }

        /**
         * Directory containing the module task.xml file used to create this TaskFactory.
         * @return The module relative path (e.g, "pipeline/tasks" for a task declared in "pipeline/tasks/foo.task.xml".)
         */
        public Path getModuleTaskPath()
        {
            return _moduleTaskPath;
        }

        protected void setModuleTaskPath(Path moduleTaskPath)
        {
            _moduleTaskPath = moduleTaskPath;
        }
    }

    public CommandTaskImpl(PipelineJob job, Factory factory)
    {
        super(factory, job);
    }

    public FileAnalysisJobSupport getJobSupport()
    {
        return getJob().getJobSupport(FileAnalysisJobSupport.class);
    }

    @Override
    public String getInstallPath()
    {
        return _factory.getInstallPath();
    }

    /**
     * Returns a file path to a resource from the declaring module.
     */
    protected String getModuleResourcePath(String path)
    {
        Module module = _factory.getDeclaringModule();
        if (module == null)
            return null;

        Resource dir = module.getModuleResource(path);
        if (dir == null || !(dir instanceof FileResource))
            return null;

        File f = ((FileResource)dir).getFile();
        return f.getPath();
    }

    public String[] getProcessPaths(WorkDirectory.Function f, String key) throws IOException
    {
        if (f == WorkDirectory.Function.module)
        {
            String path = getModuleResourcePath(key);
            return path == null ? new String[0] : new String[] { path };
        }
        else
        {
            TaskPath tp = (WorkDirectory.Function.input.equals(f) ?
                    _factory.getInputPaths().get(key) : _factory.getOutputPaths().get(key));

            ArrayList<String> paths = new ArrayList<>();
            for (File file : _wd.getWorkFiles(f, tp))
                paths.add(_wd.getRelativePath(file));
            return paths.toArray(new String[paths.size()]);
        }
    }

    private void inputFile(TaskPath tp, String role, RecordedAction action) throws IOException
    {
        List<File> filesInput = _wd.getWorkFiles(WorkDirectory.Function.input, tp);
        for (File fileInput : filesInput)
        {
            // Nothing to do, if this file is optional and does not exist.
            if (tp.isOptional() && !NetworkDrive.exists(fileInput))
                return;

            _wd.inputFile(fileInput, tp.isCopyInput() || _factory.isCopyInput());
            action.addInput(fileInput, role);
        }
    }

    protected void writeTaskInfo(File file) throws IOException
    {
        RowMapFactory<Object> factory = new RowMapFactory<>("Name", "Value", "Type");
        List<Map<String, Object>> rows = new ArrayList<>();

        rows.add(factory.getRowMap("taskId", _factory.getId()));

        for (Map.Entry<String, TaskPath> entry : _factory.getInputPaths().entrySet())
        {
            String key = entry.getKey();
            TaskPath path = entry.getValue();

            // CONSIDER: Include the TaskPath information (optional, etc.)

            String[] inputPaths = getProcessPaths(WorkDirectory.Function.input, key);
            for (String inputPath : inputPaths)
            {
                rows.add(factory.getRowMap(key, inputPath, path.getType().getDefaultSuffix()));
            }
        }

        for (Map.Entry<String, TaskPath> entry : _factory.getOutputPaths().entrySet())
        {
            String key = entry.getKey();
            TaskPath path = entry.getValue();

            // CONSIDER: Include the TaskPath information (optional, etc.)

            String[] inputPaths = getProcessPaths(WorkDirectory.Function.output, key);
            for (String inputPath : inputPaths)
            {
                rows.add(factory.getRowMap(key, inputPath, path.getType().getDefaultSuffix()));
            }
        }

        TSVMapWriter tsvWriter = new TSVMapWriter(rows);
        tsvWriter.setHeaderRowVisible(false);
        tsvWriter.write(file);
    }


    public RecordedActionSet run() throws PipelineJobException
    {
        try
        {
            RecordedAction action = new RecordedAction(_factory.getProtocolActionName());
            
            // Input file location must be determined before creating the process command.
            if (!_factory.getInputPaths().isEmpty())
            {
                try (WorkDirectory.CopyingResource lock = _wd.ensureCopyingLock())
                {
                    for (Map.Entry<String, TaskPath> entry : _factory.getInputPaths().entrySet())
                    {
                        TaskPath taskPath = entry.getValue();
                        String role = entry.getKey();
                        if (WorkDirectory.Function.input.toString().equals(role))
                        {
                            role = taskPath.getDefaultRole();
                        }
                        inputFile(taskPath, role, action);
                    }
                }
            }

//            // Write task properties file
//            File taskInfoFile = _wd.newFile(WorkDirectory.Function.input, TabLoader.TSV_FILE_TYPE);
//            writeTaskInfo(taskInfoFile);

            // Always copy in the parameters file. It's small and in some cases is useful to the process we're launching
            _wd.inputFile(getJobSupport().getParametersFile(), true);

            if (!runCommand(action))
                return new RecordedActionSet();

            // Get rid of any copied input files.
            _wd.discardCopiedInputs();

            _wd.acceptFilesAsOutputs(_factory.getOutputPaths(), action);

            // Get rid of the work directory, which should now be empty
            _wd.remove(true);

            // Should now be safe to remove the original input file, if required.
            if (_factory.isRemoveInput())
            {
                TaskPath tpInput = _factory.getInputPaths().get(WorkDirectory.Function.input.toString());
                for (File fileInput : _wd.getWorkFiles(WorkDirectory.Function.input, tpInput))
                    fileInput.delete();
            }

            return new RecordedActionSet(action);
        }
        catch (IOException e)
        {
            throw new PipelineJobException(e);
        }
        finally
        {
            _wd = null;
        }
    }

    /**
     * Run the command line task.
     * @param action The recorded action.
     * @return true if the task was run, false otherwise.
     * @throws IOException
     * @throws PipelineJobException
     */
    // TODO: Add task and job version information to the recorded action.
    protected boolean runCommand(RecordedAction action) throws IOException, PipelineJobException
    {
        ProcessBuilder pb = new ProcessBuilder(_factory.toArgs(this));
        applyEnvironment(pb);

        List<String> args = pb.command();

        if (args.size() == 0)
            return false;

        String commandLine = StringUtils.join(args, " ");

        // Just output the command line, if debug mode is set.
        if (_factory.isPreview())
        {
            getJob().header(args.get(0) + " output");
            getJob().info(commandLine);

            return false;
        }

        // Check if output file is to be generated from the stdout
        // stream of the process.
        File fileOutput = null;
        int lineInterval = 0;
        if (_factory.isPipeToOutput())
        {
            TaskPath tpOut = _factory.getOutputPaths().get(WorkDirectory.Function.output.toString());
            assert !tpOut.isSplitFiles() : "Invalid attempt to pipe output to split files.";
            fileOutput = _wd.newWorkFile(WorkDirectory.Function.output,
                    tpOut, getJobSupport().getBaseName());
            lineInterval = _factory.getPipeOutputLineInterval();
        }

        action.addParameter(RecordedAction.COMMAND_LINE_PARAM, commandLine);
        getJob().runSubProcess(pb, _wd.getDir(), fileOutput, lineInterval, false);
        return true;
    }

    private static final Pattern pat = Pattern.compile("\\$\\{[^}]*}");

    public String variableSubstitution(String src, Map<String, String> map)
    {
        StringBuffer sb = new StringBuffer();
        Matcher matcher = pat.matcher(src);
        while (matcher.find())
        {
            String varName = src.substring(matcher.start() + 2, matcher.end() - 1);

            String substValue = map.get(varName);
            if (substValue == null)
            {
                //by default substitute "" for unmatched substitutions
                substValue = "";
            }

            matcher.appendReplacement(sb, substValue);
        }
        matcher.appendTail(sb);

        return sb.toString();
    }

    public static class TestCase extends Assert
    {
        @Test
        public void testSubstitution()
        {
            Mockery context = new Mockery();
            context.setImposteriser(ClassImposteriser.INSTANCE);
            Factory factory = new Factory();
            CommandTaskImpl impl = new CommandTaskImpl(context.mock(PipelineJob.class), factory);

            assertEquals("/originalPath:/morePath", impl.variableSubstitution("${TEST}:/morePath", Collections.singletonMap("TEST", "/originalPath")));
            assertEquals(":/morePath", impl.variableSubstitution("${TEST}:/morePath", Collections.<String, String>emptyMap()));
            assertEquals("/originalPath:/morePath:/originalPath", impl.variableSubstitution("${TEST}:/morePath:${TEST}", Collections.singletonMap("TEST", "/originalPath")));
        }
    }

    private void applyEnvironment(ProcessBuilder pb)
    {
        Map<String, String> originalEnvironment = new HashMap<>(pb.environment());
        for (Map.Entry<String, String> entry : _factory._environment.entrySet())
        {
            pb.environment().put(entry.getKey(), variableSubstitution(entry.getValue(), originalEnvironment));
        }
    }
}
