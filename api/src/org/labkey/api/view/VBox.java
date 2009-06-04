/*
 * Copyright (c) 2004-2009 Fred Hutchinson Cancer Research Center
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
package org.labkey.api.view;

import org.springframework.web.servlet.ModelAndView;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;


public class VBox extends WebPartView
{
    protected List<ModelAndView> _views;

    public VBox(ModelAndView... views)
    {
        _views = new ArrayList<ModelAndView>(Arrays.asList(views));
        setFrame(FrameType.NONE);
    }


    public boolean isVisible()
    {
        return null != _views && !_views.isEmpty();
    }


    public void addView(ModelAndView v)
    {
        if (null == v)
            return;
        _views.add(v);
    }

    public List<ModelAndView> getViews()
    {
        ArrayList<ModelAndView> ret = new ArrayList<ModelAndView>(_views.size());
        ret.addAll(_views);
        return ret;
    }

    @Override
    public void renderView(Object model, HttpServletRequest request, HttpServletResponse response) throws Exception
    {
        PrintWriter out = response.getWriter();

        for (ModelAndView view : _views)
        {
            if (null == view)
                continue;
            include(view);
            out.println("<br>");
        }
    }
}