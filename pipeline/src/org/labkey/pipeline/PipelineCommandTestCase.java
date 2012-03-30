/*
 * Copyright (c) 2012 LabKey Corporation
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
package org.labkey.pipeline;

import junit.framework.Assert;
import org.junit.Test;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineService;
import org.labkey.api.pipeline.cmd.BooleanToSwitch;
import org.labkey.api.pipeline.cmd.ListToCommandArgs;
import org.labkey.api.pipeline.cmd.RequiredSwitch;
import org.labkey.api.pipeline.cmd.TaskToCommandArgs;
import org.labkey.api.pipeline.cmd.UnixCompactSwitchFormat;
import org.labkey.api.pipeline.cmd.UnixNewSwitchFormat;
import org.labkey.api.pipeline.cmd.UnixSwitchFormat;
import org.labkey.api.pipeline.cmd.ValueToMultiCommandArgs;
import org.labkey.api.pipeline.cmd.ValueToSwitch;
import org.labkey.api.pipeline.cmd.ValueWithSwitch;
import org.labkey.api.util.URLHelper;
import org.labkey.api.view.ViewBackgroundInfo;
import org.labkey.pipeline.analysis.CommandTaskImpl;

import java.sql.SQLException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

/**
 * User: cnathe
 * Date: Mar 20, 2012
 */
public class PipelineCommandTestCase extends Assert
{
        @Test
        public void testPipelineIndividualCmds() throws Exception
        {
            // test the different PipelineCommands used in the ms2Context.xml
            // with different switch formats

            ValueWithSwitch test1 = new ValueWithSwitch();
            test1.setSwitchFormat(new UnixSwitchFormat());
            test1.setSwitchName("a");
            String[] args1 = test1.toArgs("Test1");
            assertEquals("Unexpected length for ValueWithSwitch args", 2, args1.length);
            assertEquals("Unexpected arg for ValueWithSwitch", "-a", args1[0]);
            assertEquals("Unexpected arg for ValueWithSwitch", "Test1", args1[1]);
            args1 = test1.toArgs(null);
            assertEquals("Unexpected length for ValueWithSwitch args", 0, args1.length);

            BooleanToSwitch test2 = new BooleanToSwitch();
            test2.setSwitchFormat(new UnixSwitchFormat());
            test2.setSwitchName("b");
            String[] args2 = test2.toArgs("yes");
            assertEquals("Unexpected length for BooleanToSwitch args", 1, args2.length);
            assertEquals("Unexpected arg for ValueWithSwitch", "-b", args2[0]);
            args2 = test2.toArgs("no");
            assertEquals("Unexpected length for BooleanToSwitch args", 0, args2.length);
            args2 = test2.toArgs("somethingNotYesOrNo");
            assertEquals("Unexpected length for BooleanToSwitch args", 0, args2.length);
            test2.setDefault("yes");
            args2 = test2.toArgs(null);
            assertEquals("Unexpected length for BooleanToSwitch args", 1, args2.length);

            RequiredSwitch test3 = new RequiredSwitch();
            test3.setSwitchFormat(new UnixNewSwitchFormat());
            test3.setSwitchName("c");
            String[] args3 = test3.toArgsInner(null, null);
            assertEquals("Unexpected length for RequiredSwitch args", 1, args3.length);
            assertEquals("Unexpected arg for RequiredSwitch", "--c", args3[0]);
            test3.setValue("Test3");
            args3 = test3.toArgsInner(null, null);
            assertEquals("Unexpected length for RequiredSwitch args", 1, args3.length);
            assertEquals("Unexpected arg for RequiredSwitch", "--c=Test3", args3[0]);

            ValueToSwitch test4 = new ValueToSwitch();
            test4.setSwitchFormat(new UnixCompactSwitchFormat());
            test4.setSwitchName("d");
            String[] args4 = test4.toArgs("anything");
            assertEquals("Unexpected length for ValueToSwitch args", 1, args4.length);
            assertEquals("Unexpected arg for ValueToSwitch", "-d", args4[0]);
            args4 = test4.toArgs(null);
            assertEquals("Unexpected length for ValueToSwitch args", 0, args4.length);

            ValueToMultiCommandArgs test5 = new ValueToMultiCommandArgs();
            test5.setDelimiter(" ");
            String[] args5 = test5.toArgs("Test5 -100");
            assertEquals("Unexpected length for ValueToMultiCommandArgs args", 2, args5.length);
            assertEquals("Unexpected arg for ValueToMultiCommandArgs", "Test5", args5[0]);
            assertEquals("Unexpected arg for ValueToMultiCommandArgs", "-100", args5[1]);
            test5.setDelimiter("-");
            args5 = test5.toArgs("Test5 -100");
            assertEquals("Unexpected length for ValueToMultiCommandArgs args", 2, args5.length);
            assertEquals("Unexpected arg for ValueToMultiCommandArgs", "Test5 ", args5[0]);
            assertEquals("Unexpected arg for ValueToMultiCommandArgs", "100", args5[1]);
            args5 = test5.toArgs(null);
            assertEquals("Unexpected length for ValueToMultiCommandArgs args", 0, args5.length);
        }

        @Test
        public void testPipelineCombinedCmds() throws Exception
        {
            // test stringing together a set of command parameters to view the resulting command args

            RequiredSwitch test1 = new RequiredSwitch();
            test1.setSwitchFormat(new UnixSwitchFormat());
            test1.setSwitchName("a");
            test1.setValue("100");

            BooleanToSwitch test2 = new BooleanToSwitch();
            test2.setSwitchFormat(new UnixSwitchFormat());
            test2.setParameter("test, boolean to switch");
            test2.setSwitchName("b");

            ValueWithSwitch test3 = new ValueWithSwitch();
            test3.setSwitchFormat(new UnixSwitchFormat());
            test3.setParameter("test, value with switch");
            test3.setSwitchName("c");

            ValueToSwitch test4 = new ValueToSwitch();
            test4.setSwitchFormat(new UnixCompactSwitchFormat());
            test4.setParameter("test, value to switch with multi args");
            test4.setSwitchName("d");

            ValueToMultiCommandArgs test5 = new ValueToMultiCommandArgs();
            test5.setParameter("test, value to switch with multi args");
            test5.setDelimiter(" ");

            ListToCommandArgs commandList = new ListToCommandArgs();
            commandList.addConverter(test1);
            commandList.addConverter(test2);
            commandList.addConverter(test3);
            commandList.addConverter(test4);
            commandList.addConverter(test5);

            Container root = ContainerManager.createFakeContainer(null, null);
            Container c = ContainerManager.createFakeContainer("test", root);

            // expected param args to be : -a 100 -b -c testing -d testing2 100 -999
            TestJob j = new TestJob(c);
            j.addParameter("test, boolean to switch", "yes");
            j.addParameter("test, value with switch", "testing");
            j.addParameter("test, value to switch with multi args", "testing2 100 -999");
            String[] args = commandList.toArgs(new CommandTaskImpl(j, new CommandTaskImpl.Factory()), new HashSet<TaskToCommandArgs>());
            assertEquals("Unexpected length for args", 9, args.length);
            assertEquals("Unexpected arg for RequiredSwitch", "-a", args[0]);
            assertEquals("Unexpected arg for RequiredSwitch", "100", args[1]);
            assertEquals("Unexpected arg for BooleanToSwitch", "-b", args[2]);
            assertEquals("Unexpected arg for ValueWithSwitch", "-c", args[3]);
            assertEquals("Unexpected arg for ValueWithSwitch", "testing", args[4]);
            assertEquals("Unexpected arg for ValueToSwitch", "-d", args[5]);
            assertEquals("Unexpected arg for ValueToMultiCommandArgs", "testing2", args[6]);
            assertEquals("Unexpected arg for ValueToMultiCommandArgs", "100", args[7]);
            assertEquals("Unexpected arg for ValueToMultiCommandArgs", "-999", args[8]);

            // expected param args to be : -a 100
            TestJob j2 = new TestJob(c);
            j2.addParameter("test, boolean to switch", "no");
            j2.addParameter("test, value with switch", null);
            j2.addParameter("test, value to switch with multi args", "");
            String[] args2 = commandList.toArgs(new CommandTaskImpl(j2, new CommandTaskImpl.Factory()), new HashSet<TaskToCommandArgs>());
            assertEquals("Unexpected length for args2", 2, args2.length);
            assertEquals("Unexpected arg for RequiredSwitch", "-a", args2[0]);
            assertEquals("Unexpected arg for RequiredSwitch", "100", args2[1]);
        }

    private static class TestJob extends PipelineJob
    {
        Map<String, String> _testParams = new HashMap<String, String>();

        TestJob(Container c) throws SQLException
        {
            super(null, new ViewBackgroundInfo(c, null, null), PipelineService.get().findPipelineRoot(c));
        }

        public void addParameter(String key, String value)
        {
            _testParams.put(key, value);
        }

        @Override
        public Map<String, String> getParameters()
        {
            return _testParams;
        }

        @Override
        public String getDescription()
        {
            return "test job";
        }

        @Override
        public URLHelper getStatusHref()
        {
            return null;
        }
    }
}
