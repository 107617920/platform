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
package org.labkey.api.util;

import org.apache.batik.transcoder.TranscoderException;
import org.labkey.api.attachments.DocumentConversionService;
import org.labkey.api.services.ServiceRegistry;
import org.labkey.api.thumbnail.Thumbnail;
import org.labkey.api.thumbnail.ThumbnailOutputStream;

/**
 * User: adam
 * Date: 10/4/12
 * Time: 5:34 AM
 */
public class ThumbnailUtil
{
    public static Thumbnail getThumbnailFromSvg(String svg)
    {
        if (null != svg)
        {
            try
            {
                DocumentConversionService svc = ServiceRegistry.get().getService(DocumentConversionService.class);

                if (null != svc)
                {
                    ThumbnailOutputStream os = new ThumbnailOutputStream();
                    svc.svgToPng(svg, os, ImageUtil.THUMBNAIL_HEIGHT);

                    return os.getThumbnail("image/png");
                }
            }
            catch (TranscoderException e)
            {
                throw new RuntimeException(e);
            }
        }

        return null;
    }
}
