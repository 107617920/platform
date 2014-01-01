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

import org.apache.log4j.Logger;
import org.apache.xmlbeans.XmlException;
import org.apache.xmlbeans.XmlObject;
import org.apache.xmlbeans.XmlOptions;
import org.jetbrains.annotations.NotNull;
import org.labkey.api.action.HasViewContext;
import org.labkey.api.module.Module;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.pipeline.*;
import org.labkey.api.pipeline.file.FileAnalysisTaskPipeline;
import org.labkey.api.pipeline.file.FileAnalysisTaskPipelineSettings;
import org.labkey.api.resource.Resource;
import org.labkey.api.util.*;
import org.labkey.api.view.HttpView;
import org.labkey.api.data.Container;
import org.labkey.api.view.ViewContext;
import org.labkey.pipeline.api.PipelineJobServiceImpl;
import org.labkey.pipeline.api.TaskPipelineImpl;
import org.labkey.pipeline.xml.PipelineDocument;
import org.labkey.pipeline.xml.TaskPipelineType;
import org.labkey.pipeline.xml.TaskRefType;
import org.labkey.pipeline.xml.TaskType;

import java.io.FileFilter;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * <code>FileAnalysisTaskPipelineImp</code>
 */
public class FileAnalysisTaskPipelineImpl extends TaskPipelineImpl<FileAnalysisTaskPipelineSettings> implements FileAnalysisTaskPipeline, Cloneable
{
    /** The text that will appear in the button to start this pipeline. */
    private String _description = "Analyze Data";
    private String _protocolFactoryName;
    private String _analyzeURL;
    private boolean _initialFileTypesFromTask;
    private List<FileType> _initialFileTypes;
    private Map<FileType, FileType[]> _typeHierarchy;
    /** If set, the default location for the action in the UI */
    private PipelineActionConfig.displayState _defaultDisplayState;

    public FileAnalysisTaskPipelineImpl()
    {
        super(new TaskId(FileAnalysisTaskPipeline.class));
    }

    public FileAnalysisTaskPipelineImpl(TaskId taskId)
    {
        super(taskId);
    }

    public TaskPipeline cloneAndConfigure(FileAnalysisTaskPipelineSettings settings, TaskId[] taskProgression) throws CloneNotSupportedException
    {
        FileAnalysisTaskPipelineImpl pipeline = (FileAnalysisTaskPipelineImpl)
                super.cloneAndConfigure(settings, taskProgression);

        return pipeline.configure(settings);
    }

    private TaskPipeline<FileAnalysisTaskPipelineSettings> configure(FileAnalysisTaskPipelineSettings settings)
    {
        if (settings.getDescription() != null)
            _description = settings.getDescription();

        if (settings.getProtocolFactoryName() != null)
            _protocolFactoryName = settings.getProtocolFactoryName();

        if (settings.getAnalyzeURL() != null)
            _analyzeURL = settings.getAnalyzeURL();

        // Convert any input filter extensions to array of file types.
        List<FileType> inputFilterExts = settings.getInitialInputExts();
        if (inputFilterExts != null)
        {
            _initialFileTypesFromTask = false;
            _initialFileTypes = inputFilterExts;
        }
        else if (_initialFileTypesFromTask || getInitialFileTypes() == null)
        {
            _initialFileTypesFromTask = true;
            TaskId tid = getTaskProgression()[0];
            TaskFactory factory = PipelineJobService.get().getTaskFactory(tid);
            _initialFileTypes = factory.getInputTypes();
        }

        // Misconfiguration: the user will never be able to start this pipeline
        if (_initialFileTypes == null || _initialFileTypes.isEmpty())
                throw new IllegalArgumentException("File analysis pipelines require at least one initial file type.");

        // Convert any input extension hierarchy into file types.
        Map<FileType, List<FileType>> extHierarchy = settings.getFileExtHierarchy();
        if (extHierarchy != null || _typeHierarchy == null)
            _typeHierarchy = new HashMap<>();

        // Add the initial types to the hierarchy
        for (FileType ft : _initialFileTypes)
            _typeHierarchy.put(ft, new FileType[0]);

        if (extHierarchy != null)
        {
            for (Map.Entry<FileType, List<FileType>> entry  : extHierarchy.entrySet())
            {
                List<FileType> inputExtList = entry.getValue();
                FileType[] hierarchy = inputExtList.toArray(new FileType[inputExtList.size()]);
                _typeHierarchy.put(entry.getKey(), hierarchy);
            }
        }

        if (settings.getDefaultDisplayState() != null)
        {
            _defaultDisplayState = settings.getDefaultDisplayState();
        }

        return this;
    }

    public PipelineActionConfig.displayState getDefaultDisplayState()
    {
        return _defaultDisplayState;
    }

    public String getDescription()
    {
        return _description;
    }

    public String getProtocolFactoryName()
    {
        return _protocolFactoryName;
    }

    @NotNull
    public List<FileType> getInitialFileTypes()
    {
        return _initialFileTypes;
    }

    @NotNull
    public FileFilter getInitialFileTypeFilter()
    {
        return new PipelineProvider.FileTypesEntryFilter(_initialFileTypes);
    }

    @NotNull
    public URLHelper getAnalyzeURL(Container c, String path)
    {
        if (_analyzeURL != null)
        {
            try
            {
                ViewContext context = HttpView.currentContext();
                StringExpression expressionCopy = StringExpressionFactory.createURL(_analyzeURL);
                if (expressionCopy instanceof HasViewContext)
                    ((HasViewContext)expressionCopy).setViewContext(context);
                URLHelper result = new URLHelper(expressionCopy.eval(context.getExtendedProperties()));
                if (result.getParameter("path") == null)
                {
                    result.addParameter("path", path);
                }
                return result;
            }
            catch (URISyntaxException e)
            {
                throw new UnexpectedException(e);
            }
        }
        return AnalysisController.urlAnalyze(c, getId(), path);
    }

    @NotNull
    public Map<FileType, FileType[]> getTypeHierarchy()
    {
        return _typeHierarchy;
    }

    @Override
    public String toString()
    {
        return getDescription();
    }

    /**
     * Creates TaskPipeline from a file-based module <code>&lt;name>.pipeline.xml</code> config file.
     *
     * @param pipelineTaskId The taskid of the TaskPipeline
     * @param pipelineConfig The task pipeline definition.
     */
    public static FileAnalysisTaskPipeline create(TaskId pipelineTaskId, Resource pipelineConfig)
    {
        if (pipelineTaskId.getName() == null)
            throw new IllegalArgumentException("Task pipeline must by named");

        if (pipelineTaskId.getType() != TaskId.Type.pipeline)
            throw new IllegalArgumentException("Task pipeline must by of type 'pipeline'");

        if (pipelineTaskId.getModuleName() == null)
            throw new IllegalArgumentException("Task pipeline must be defined by a module");

        Module module = ModuleLoader.getInstance().getModule(pipelineTaskId.getModuleName());

        PipelineDocument doc;
        try
        {
            XmlOptions options = XmlBeansUtil.getDefaultParseOptions();
            doc = PipelineDocument.Factory.parse(pipelineConfig.getInputStream(), options);
            XmlBeansUtil.validateXmlDocument(doc, "Task pipeline config '" + pipelineConfig.getPath() + "'");
        }
        catch (XmlValidationException e)
        {
            Logger.getLogger(PipelineJobServiceImpl.class).error(e);
            return null;
        }
        catch (XmlException |IOException e)
        {
            Logger.getLogger(PipelineJobServiceImpl.class).error("Error loading task pipeline '" + pipelineConfig.getPath() + "':\n" + e.getMessage());
            return null;
        }

        FileAnalysisTaskPipelineImpl pipeline = new FileAnalysisTaskPipelineImpl(pipelineTaskId);
        pipeline.setDeclaringModule(module);

        TaskPipelineType xpipeline = doc.getPipeline();
        if (xpipeline == null)
            throw new IllegalArgumentException("<pipeline> element required");

        if (!pipelineTaskId.getName().equals(xpipeline.getName()))
            throw new IllegalArgumentException(String.format("Task pipeline must have the name '%s'", pipelineTaskId.getName()));

        if (xpipeline.isSetDescription())
            pipeline._description = xpipeline.getDescription();

        if (xpipeline.isSetAnalyzeURL())
            pipeline._analyzeURL = xpipeline.getAnalyzeURL();

        // Resolve all the steps in the pipeline
        List<TaskId> progression = new ArrayList<>();
        XmlObject[] xtasks = xpipeline.getTasks().selectPath("./*");
        for (int taskIndex = 0; taskIndex < xtasks.length; taskIndex++)
        {
            XmlObject xobj = xtasks[taskIndex];
            if (xobj instanceof TaskRefType)
            {
                TaskRefType xtaskref = (TaskRefType)xobj;
                try
                {
                    TaskId taskId = TaskId.valueOf(xtaskref.getRef());
                    TaskFactory factory = PipelineJobService.get().getTaskFactory(taskId);
                    if (factory == null)
                        throw new IllegalArgumentException("Task factory ref not found: " + xtaskref.getRef());

                    // UNDONE: Use settings to configure a task reference
                    /*
                    if (xtaskref.isSetSettings())
                    {
                        // Create settings from xml
                        TaskFactorySettings settings = createSettings(pipelineTaskId, factory, xtaskref.getSettings());
                        if (settings.getId().equals(taskId))
                            throw new IllegalArgumentException("Task factory settings must not be identical to parent task: " + settings.getId());

                        // Register locally configured task
                        try
                        {
                            PipelineJobServiceImpl.get().addLocalTaskFactory(pipeline, settings);
                        }
                        catch (CloneNotSupportedException e)
                        {
                            throw new IllegalArgumentException("Failed to register task with settings: " + taskId, e);
                        }

                        taskId = settings.getId();
                    }
                    */

                    progression.add(taskId);
                }
                catch (ClassNotFoundException cnfe)
                {
                    throw new IllegalArgumentException("Task factory class not found: " + xtaskref.getRef());
                }

            }
            else if (xobj instanceof TaskType)
            {
                // Create a new local task definition
                TaskType xtask = (TaskType)xobj;

                String name = xtask.schemaType().getName().getLocalPart() + "-" + String.valueOf(taskIndex);
                if (xtask.isSetName())
                    name = xtask.getName();

                TaskId taskId = createLocalTaskId(pipelineTaskId, name);

                Path tasksDir = pipelineConfig.getPath().getParent();
                TaskFactory factory = PipelineJobServiceImpl.get().createTaskFactory(taskId, xtask, tasksDir);
                if (factory == null)
                    throw new IllegalArgumentException("Task factory not found: " + taskId);

                PipelineJobServiceImpl.get().addLocalTaskFactory(pipeline, factory);

                progression.add(taskId);
            }
        }

        if (progression.isEmpty())
            throw new IllegalArgumentException("Expected at least one task factory in the task pipeline");

        TaskFactory initialTaskFactory = PipelineJobService.get().getTaskFactory(progression.get(0));
        if (initialTaskFactory == null)
            throw new IllegalArgumentException("Expected at least one task factory in the task pipeline");

        pipeline.setTaskProgression(progression.toArray(new TaskId[progression.size()]));

        // Initial file types
        pipeline._initialFileTypesFromTask = true;
        pipeline._initialFileTypes = initialTaskFactory.getInputTypes();

        // Misconfiguration: the user will never be able to start this pipeline
        if (pipeline._initialFileTypes == null || pipeline._initialFileTypes.isEmpty())
            throw new IllegalArgumentException("File analysis pipelines require at least one initial file type.");

        // CONSIDER: Attempt to map outputs from previous task to inputs of the next task

        // UNDONE: I don't understand the typeHierarchy
        // Add the initial types to the hierarchy
        pipeline._typeHierarchy = new HashMap<>();
        for (FileType ft : pipeline._initialFileTypes)
            pipeline._typeHierarchy.put(ft, new FileType[0]);

//        // UNDONE: Default display state
//        if (xpipeline.isSetDefaultDisplay())
//            pipeline._defaultDisplayState = PipelineActionConfig.displayState.valueOf(xpipeline.getDefaultDisplayState());

        return pipeline;
    }

    private static TaskId createLocalTaskId(TaskId pipelineTaskId, String name)
    {
        String taskName = pipelineTaskId.getName() + "/" + name;
        return new TaskId(pipelineTaskId.getModuleName(), TaskId.Type.task, taskName, pipelineTaskId.getVersion());
    }

    /*
    private static TaskFactorySettings createSettings(TaskId pipelineTaskId, TaskFactory factory, XmlObject xsettings)
    {
        TaskId parentTaskId = factory.getId();
        TaskId taskId = createLocalTaskId(pipelineTaskId, parentTaskId.getName());

        TaskFactorySettings settings = createFactorySettings(factory, taskId);

        // UNDONE: I'm tired. Need to set the values on the settings reflectivly using xml->bean stuff
        if (settings instanceof org.labkey.api.assay.pipeline.AssayImportRunTaskFactorySettings)
        {
            org.labkey.api.assay.pipeline.AssayImportRunTaskFactorySettings airtfs = (org.labkey.api.assay.pipeline.AssayImportRunTaskFactorySettings)settings;

            XmlObject xproviderName = xsettings.selectChildren("http://labkey.org/pipeline/xml", "providerName")[0];
            String providerName = ((XmlObjectBase)xproviderName).getStringValue();
            airtfs.setProviderName(providerName);

            XmlObject xprotocolName = xsettings.selectChildren("http://labkey.org/pipeline/xml", "protocolName")[0];
            String protocolName = ((XmlObjectBase)xprotocolName).getStringValue();
            airtfs.setProtocolName(protocolName);
        }

        return settings;
    }

    // Reflection hack to create instance of appropriate TaskFactorySettings class
    private static TaskFactorySettings createFactorySettings(TaskFactory factory, TaskId taskId)
    {
        Class clazz = factory.getClass();

        // Look for a "configure" method up the class hierarchy
        Class<? extends TaskFactorySettings> typeBest = null;
        while (clazz != null)
        {
            for (Method m : clazz.getDeclaredMethods())
            {
                if (m.getName().equals("configure") || m.getName().equals("cloneAndConfigure"))
                {
                    Class[] types = m.getParameterTypes();
                    if (types.length != 1)
                        continue;

                    Class typeCurrent = types[0];
                    if (!TaskFactorySettings.class.isAssignableFrom(typeCurrent))
                        continue;

                    if (typeBest == null || typeBest.isAssignableFrom(typeCurrent))
                        typeBest = typeCurrent.asSubclass(TaskFactorySettings.class);
                }
            }

            clazz = clazz.getSuperclass();
        }

        if (typeBest == null)
            throw new IllegalArgumentException("TaskFactory settings class not found for type: " + clazz.getName());

        try
        {
            Constructor<? extends TaskFactorySettings> ctor = typeBest.getConstructor(TaskId.class);
            return ctor.newInstance(taskId);
        }
        catch (ReflectiveOperationException e)
        {
            throw new IllegalArgumentException("Error creating TaskFactorySettings: " + typeBest.getName(), e);
        }
    }
    */
}
