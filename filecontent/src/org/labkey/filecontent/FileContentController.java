/*
 * Copyright (c) 2006-2013 LabKey Corporation
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

package org.labkey.filecontent;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.json.JSONArray;
import org.json.JSONObject;
import org.labkey.api.action.ApiAction;
import org.labkey.api.action.ApiJsonWriter;
import org.labkey.api.action.ApiResponse;
import org.labkey.api.action.ApiResponseWriter;
import org.labkey.api.action.ApiSimpleResponse;
import org.labkey.api.action.ConfirmAction;
import org.labkey.api.action.CustomApiForm;
import org.labkey.api.action.ExtFormAction;
import org.labkey.api.action.FormViewAction;
import org.labkey.api.action.GWTServiceAction;
import org.labkey.api.action.MutatingApiAction;
import org.labkey.api.action.ReturnUrlForm;
import org.labkey.api.action.SimpleViewAction;
import org.labkey.api.action.SpringActionController;
import org.labkey.api.admin.AdminUrls;
import org.labkey.api.attachments.AttachmentDirectory;
import org.labkey.api.attachments.AttachmentForm;
import org.labkey.api.attachments.AttachmentService;
import org.labkey.api.attachments.SpringAttachmentFile;
import org.labkey.api.audit.AuditLogService;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.exp.DomainDescriptor;
import org.labkey.api.exp.OntologyManager;
import org.labkey.api.exp.api.ExpData;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.exp.property.IPropertyValidator;
import org.labkey.api.exp.property.PropertyService;
import org.labkey.api.exp.property.ValidatorContext;
import org.labkey.api.exp.query.ExpDataTable;
import org.labkey.api.exp.query.ExpSchema;
import org.labkey.api.files.FileContentDefaultEmailPref;
import org.labkey.api.files.FileContentEmailPref;
import org.labkey.api.files.FileContentService;
import org.labkey.api.files.FileUrls;
import org.labkey.api.files.FilesAdminOptions;
import org.labkey.api.files.FilesTbarBtnOption;
import org.labkey.api.files.MissingRootDirectoryException;
import org.labkey.api.files.UnsetRootDirectoryException;
import org.labkey.api.files.view.FilesWebPart;
import org.labkey.api.gwt.server.BaseRemoteService;
import org.labkey.api.message.settings.MessageConfigService;
import org.labkey.api.notification.EmailService;
import org.labkey.api.pipeline.PipeRoot;
import org.labkey.api.pipeline.PipelineActionConfig;
import org.labkey.api.pipeline.PipelineService;
import org.labkey.api.pipeline.PipelineUrls;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.PropertyValidationError;
import org.labkey.api.query.QuerySettings;
import org.labkey.api.query.QueryUpdateService;
import org.labkey.api.query.QueryUpdateServiceException;
import org.labkey.api.query.QueryView;
import org.labkey.api.query.UserSchema;
import org.labkey.api.query.ValidationError;
import org.labkey.api.query.ValidationException;
import org.labkey.api.security.RequiresPermissionClass;
import org.labkey.api.security.RequiresSiteAdmin;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.security.permissions.DeletePermission;
import org.labkey.api.security.permissions.InsertPermission;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.services.ServiceRegistry;
import org.labkey.api.util.ExceptionUtil;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.MimeMap;
import org.labkey.api.util.NetworkDrive;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.Path;
import org.labkey.api.util.URLHelper;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.GWTView;
import org.labkey.api.view.HtmlView;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.JspView;
import org.labkey.api.view.NavTree;
import org.labkey.api.view.NotFoundException;
import org.labkey.api.view.Portal;
import org.labkey.api.view.ViewContext;
import org.labkey.api.view.WebPartView;
import org.labkey.api.view.template.PageConfig;
import org.labkey.api.webdav.FileSystemAuditProvider;
import org.labkey.api.webdav.FileSystemAuditViewFactory;
import org.labkey.api.webdav.FileSystemResource;
import org.labkey.api.webdav.WebdavResource;
import org.labkey.api.webdav.WebdavService;
import org.labkey.filecontent.message.FileEmailConfig;
import org.labkey.filecontent.message.ShortMessageDigest;
import org.springframework.validation.BindException;
import org.springframework.validation.Errors;
import org.springframework.validation.ObjectError;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;


public class FileContentController extends SpringActionController
{
   public enum RenderStyle
   {
       DEFAULT,     // call defaultRenderStyle
       FRAME,       // <iframe>                       (*)
       INLINE,      // use INCLUDE (INLINE is confusing, does NOT mean content-disposition:inline)
       INCLUDE,     // include html                   (text/html)
       PAGE,        // content-disposition:inline     (*)
       ATTACHMENT,  // content-disposition:attachment (*)
       TEXT,        // filtered                       (text/*)
       IMAGE        // <img>                          (image/*)
   }

   private static final DefaultActionResolver _actionResolver = new DefaultActionResolver(FileContentController.class);

   public FileContentController() throws Exception
   {
       setActionResolver(_actionResolver);
   }


    public static class FileUrlsImpl implements FileUrls
    {
        public ActionURL urlBegin(Container container)
        {
            return new ActionURL(BeginAction.class, container);
        }

        public ActionURL urlShowAdmin(Container container)
        {
            return new ActionURL(ShowAdminAction.class, container);
        }

        public ActionURL urlFileEmailPreference(Container container)
        {
            return new ActionURL(FileEmailPreferenceAction.class, container);
        }
    }

    @RequiresPermissionClass(ReadPermission.class)
    public class SendFileAction extends SimpleViewAction<SendFileForm>
    {
        WebdavResource _resource;

        public ModelAndView getView(SendFileForm form, BindException errors) throws Exception
        {
            if (null == form.getFileName())
                throw new NotFoundException();

            String fileSet = StringUtils.trimToNull(form.getFileSet());
            AttachmentDirectory p;
            FileContentService svc = ServiceRegistry.get().getService(FileContentService.class);

            if (svc == null)
                throw new NotFoundException();

            if (null == fileSet)
            {
                p = svc.getMappedAttachmentDirectory(getContainer(), false);
                if (p != null && p.getFileSystemDirectory() != null)
                {
                    // For FileContent files, check if there's a newer copy in the legacy directory that needs to be
                    // moved into the @files directory
                    FileSystemResource.mergeFiles(p.getFileSystemDirectory());
                }
            }
            else
                p = svc.getRegisteredDirectory(getContainer(), fileSet);

            if (null == p)
                throw new NotFoundException();

            File dir = p.getFileSystemDirectory();
            if (null == dir)
            {
                if (getUser().isSiteAdmin())
                    return HttpView.redirect("showAdmin.view");
                else
                    throw new NotFoundException();
            }

            Path filePath = Path.parse(form.getFileName());
            Path path;

            // support both legacy and newer formats, new URLs looks like webdav URLs, while older formats assume files
            // are only served out of the root
            if (filePath.contains(FileContentService.FILES_LINK) || filePath.contains(FileContentService.FILE_SETS_LINK) ||
                    filePath.contains(FileContentService.PIPELINE_LINK))
            {
                path = WebdavService.getPath().append(getContainer().getParsedPath()).append(filePath);
            }
            // legacy format: named file set specified as parameter
            else if (fileSet != null)
            {
                path = WebdavService.getPath().append(getContainer().getParsedPath()).append(FileContentService.FILE_SETS_LINK).append(fileSet).append(filePath);
            }
            else
                path = WebdavService.getPath().append(getContainer().getParsedPath()).append(FileContentService.FILES_LINK).append(filePath);

            _resource = WebdavService.get().getResolver().lookup(path);

            if (_resource == null || !_resource.isFile())
                throw new NotFoundException();

            try {
                RenderStyle style = form.getRenderStyle();
                MimeMap mimeMap = new MimeMap();
                String mimeType = _resource.getContentType();

                if (style == RenderStyle.DEFAULT)
                {
                    style = defaultRenderStyle(_resource.getName());
                }
                else
                {
                    // verify legal RenderStyle
                    boolean canInline = mimeType.startsWith("text/") || mimeMap.isInlineImageFor(_resource.getName());
                    if (!canInline && !(RenderStyle.ATTACHMENT == style || RenderStyle.PAGE == style))
                        style = RenderStyle.PAGE;
                    if (RenderStyle.IMAGE == style && !mimeType.startsWith("image/"))
                        style = RenderStyle.PAGE;
                    if (RenderStyle.TEXT == style && !mimeType.startsWith("text/"))
                        style = RenderStyle.PAGE;
                }

                //FIX: 5523 - if renderAs is null and mimetype is HTML, default style to inline
                if (null == form.getRenderAs())
                {
                    if ("text/html".equalsIgnoreCase(mimeType))
                        style = RenderStyle.INCLUDE;
                }

                switch (style)
                {
                    case ATTACHMENT:
                    case PAGE:
                    {
                        getPageConfig().setTemplate(PageConfig.Template.None);

                        try
                        {
                        PageFlowUtil.streamFile(getViewContext().getResponse(),
                                Collections.singletonMap("Content-Type",_resource.getContentType()),
                                _resource.getName(),
                                _resource.getInputStream(getUser()),
                                RenderStyle.ATTACHMENT==style);
                        }
                        catch (FileNotFoundException x)
                        {
                            throw new NotFoundException(_resource.getName());
                        }
                        return null;
                    }
                    case FRAME:
                    {
                        URLHelper url = new URLHelper(HttpView.getContextURL());
                        url.replaceParameter("renderAs", FileContentController.RenderStyle.PAGE.toString());
                        return new IFrameView(url.getLocalURIString());
                    }
                    case INCLUDE:
                    case INLINE:
                    {
                        WebPartView webPart = new WebPartView()
                        {
                            @Override
                            protected void renderView(Object model, PrintWriter out) throws Exception
                            {
                                InputStream fis = null;
                                try
                                {
                                    if (null == _resource || !_resource.isFile())
                                        throw new FileNotFoundException();
                                    fis = _resource.getInputStream(getUser());
                                    if (null == fis)
                                        throw new FileNotFoundException();
                                    IOUtils.copy(new InputStreamReader(fis), out);
                                }
                                catch (FileNotFoundException x)
                                {
                                    out.write("<span class='labkey-error'>file not found: " + PageFlowUtil.filter(_resource.getName()) + "</span>");
                                }
                                finally
                                {
                                    IOUtils.closeQuietly(fis);
                                }
                            }
                        };
                        webPart.setTitle(_resource.getName());
                        webPart.setFrame(WebPartView.FrameType.DIV);
                        return webPart;
                    }
                    case TEXT:
                    {
                        WebPartView webPart = new WebPartView()
                        {
                            @Override
                            protected void renderView(Object model, PrintWriter out) throws Exception
                            {
                                renderResourceContents(out, _resource);
                            }
                        };
                        webPart.setTitle(_resource.getName());

                        NavTree navMenu = new NavTree();
                        navMenu.addChild(new NavTree("download " + _resource.getName(), form.getDownloadURL(getContainer())));
                        webPart.setNavMenu(navMenu);
                        return webPart;
                    }
                    case IMAGE:
                    {
                        URLHelper url = new URLHelper(HttpView.getContextURL());
                        url.replaceParameter("renderAs", FileContentController.RenderStyle.PAGE.toString());
                        return new ImgView(url.getLocalURIString());
                    }
                    default:
                        return null;
                }
            }
            catch (Exception e)
            {
                throw new NotFoundException("An error occurred with the requested file : " + e.getMessage());
            }
        }

        private void renderResourceContents(PrintWriter out, WebdavResource resource) throws IOException
        {
            StringBuilder contents = new StringBuilder();
            BufferedReader reader = new BufferedReader(new InputStreamReader(resource.getInputStream(getUser())));
            String line;
            String newline = System.getProperty("line.separator");
            final int MAX_SIZE = 5000;
            int size = 0;
            try
            {
                while ((line = reader.readLine()) != null)
                {
                    contents.append(line);
                    contents.append(newline);

                    if (size++ > MAX_SIZE)
                        break;
                }

                if (size > MAX_SIZE)
                {
                    StringBuilder sb = new StringBuilder();

                    sb.append("<span class='labkey-error'>");
                    sb.append("The requested file is too large to display on a page, only part of the file is shown. To download the entire file contents ");
                    sb.append("click on the download link below.").append("</span><br>");
                    sb.append("<br>").append("<a href=\"").append(resource.getHref(getViewContext())).append("?contentDisposition=attachment");
                    sb.append("\">download ").append(PageFlowUtil.filter(resource.getName())).append("</a>");
                    sb.append("<br><br>");

                    out.write(sb.toString());
                }

                out.write(PageFlowUtil.filter(contents.toString(), true, true));
            }
            catch (FileNotFoundException x)
            {
                out.write("<span class='labkey-error'>file not found: " + PageFlowUtil.filter(resource.getName()) + "</span>");
            }
            catch (IOException e)
            {
                out.write("<span class='labkey-error'>IOException: " + PageFlowUtil.filter(resource.getName()) + "</span>");
            }
            finally
            {
                IOUtils.closeQuietly(reader);
            }
        }

        public NavTree appendNavTrail(NavTree root)
        {
            String name = _resource == null ? "<not found>" : _resource.getName();
            return (new BeginAction()).appendNavTrail(root)
                    .addChild(name);
        }
    }


   public static class SrcForm
   {
       private String src;

       public String getSrc()
       {
           return src;
       }

       public void setSrc(String src)
       {
           this.src = src;
       }
   }


   @RequiresPermissionClass(ReadPermission.class)
   public class FrameAction extends SimpleViewAction<SrcForm>
   {
       public ModelAndView getView(SrcForm srcForm, BindException errors) throws Exception
       {
           String src = srcForm.getSrc();
           return new IFrameView(src);
       }

       public NavTree appendNavTrail(NavTree root)
       {
           return root;
       }
   }


    @RequiresPermissionClass(ReadPermission.class)
    public class BeginAction extends SimpleViewAction<FileContentForm>
    {
        public ModelAndView getView(FileContentForm form, BindException errors) throws Exception
        {
            FilesWebPart part;
            if (null == form.getFileSetName())
                part = new ManageWebPart(getContainer());
            else
                part = new ManageWebPart(getContainer(), form.getFileSetName());
            if (null != form.getPath())
            {
                try
                {
                    Path path = Path.decode(form.getPath());
                    part.getModelBean().setDirectory(path);
                }
                catch (Throwable t)
                {
                }
            }
            if (null != form.getRootOffset())
            {
                try
                {
                    String offset = Path.decode(form.getRootOffset()).toString().replaceAll("^/", "").toString();
                    String path = part.getModelBean().getRootPath();
                    path += path.endsWith("/") ? "" : "/";
                    path += part.getModelBean().getRootPath().endsWith("/") ? "" : "/";
                    path += offset;
                    part.getModelBean().setRootPath(path);
                    part.getModelBean().setRootOffset(offset);
                }
                catch (Throwable t)
                {
                }
            }
            part.setFrame(WebPartView.FrameType.NONE);
            part.setWide(true);
            part.getModelBean().setAutoResize(true);
            part.getModelBean().setShowDetails(true);
            return part;
        }

        public NavTree appendNavTrail(NavTree root)
        {
            root.addChild("Manage Files", new ActionURL(BeginAction.class, getContainer()));
            return root;
        }
    }

   private static class FileSetsWebPart extends WebPartView
   {
       private Container c;
       public FileSetsWebPart(Container c)
       {
           this.c = c;
           setTitle("File Sets");
       }


       @Override
       protected void renderView(Object model, PrintWriter out) throws Exception
       {
           FileContentService svc = ServiceRegistry.get().getService(FileContentService.class);
           AttachmentDirectory main = svc.getMappedAttachmentDirectory(c, false);
           if (null != main && null != main.getFileSystemDirectory())
               out.write("<a href='begin.view'>Default</a><br>");

           for (AttachmentDirectory attDir : svc.getRegisteredDirectories(c))
               out.write("<a href='begin.view?fileSetName=" + PageFlowUtil.filter(attDir.getLabel()) + "'>" + PageFlowUtil.filter(attDir.getLabel()) + "</a><br>");

           if (HttpView.currentContext().getUser().isSiteAdmin())
               out.write("<br>" + PageFlowUtil.textLink("Configure", "showAdmin.view"));
       }
   }


   @RequiresPermissionClass(InsertPermission.class)
   public class AddAttachmentAction extends FormViewAction<AttachmentForm>
   {
       HttpView _closeView = null;

       public void validateCommand(AttachmentForm target, Errors errors)
       {
       }

       public ModelAndView getView(AttachmentForm form, boolean reshow, BindException errors) throws Exception
       {
           getPageConfig().setTemplate(PageConfig.Template.None);

           try
           {
                return AttachmentService.get().getAddAttachmentView(getContainer(), getAttachmentParent(form), errors);
           }
           catch (NotFoundException x)
           {
                return ExceptionUtil.getErrorView(HttpServletResponse.SC_NOT_FOUND, x.getMessage(), x, getViewContext().getRequest(), false, true);
           }
       }

       public boolean handlePost(AttachmentForm form, BindException errors) throws Exception
       {
           Map<String, MultipartFile> fileMap = getFileMap();
           if (fileMap.size() > 0 && !getUser().isDeveloper())
           {
               for (MultipartFile formFile : fileMap.values())
               {
                   String contentType = new MimeMap().getContentTypeFor(formFile.getName());
                   if (formFile.getContentType().contains("html") || (null != contentType && contentType.contains("html")))
                   {
                       //This relies on storing whole file in memory. Generally OK for text files like this.
                       String html = new String(formFile.getBytes(),"UTF-8");
                       List<String> validateErrors = new ArrayList<>();
                       List<String> safetyWarnings = new ArrayList<>();
                       PageFlowUtil.validateHtml(html, validateErrors, safetyWarnings);
                       if (safetyWarnings.size() > 0)
                       {
                           addError(errors, "HTML Pages cannot contain script unless uploaded by a site administrator.");
                           //                            ActionURL reshow = getViewContext().cloneActionURL().setAction("showAddAttachment.view").replaceParameter("entityId", form.getEntityId());
                           //                            sb.append(PageFlowUtil.generateButton("Try Again", reshow));
                           //                            return includeView(new DialogTemplate(new HtmlView(sb.toString())));
                           return false;
                       }
                   }
               }
           }

           try
           {
               _closeView = AttachmentService.get().add(getAttachmentParent(form), SpringAttachmentFile.createList(fileMap), getUser());
           }
           catch (NotFoundException x)
           {
               _closeView = ExceptionUtil.getErrorView(HttpServletResponse.SC_NOT_FOUND, x.getMessage(), x, getViewContext().getRequest(), false, true);
           }

           return true;
       }

       public ModelAndView getSuccessView(AttachmentForm attachmentForm)
       {
           getPageConfig().setTemplate(PageConfig.Template.None);
           return _closeView;
       }

       public ActionURL getSuccessURL(AttachmentForm attachmentForm)
       {
           return null;
       }

       public NavTree appendNavTrail(NavTree root)
       {
           return null;
       }
   }


   @RequiresPermissionClass(DeletePermission.class)
   public class DeleteAttachmentAction extends ConfirmAction<AttachmentForm>
   {
       HttpView _closeView = null;

       public String getConfirmText()
       {
           return "Delete";
       }

       @Override
       public boolean isPopupConfirmation()
       {
           return true;
       }

       public ModelAndView getConfirmView(AttachmentForm form, BindException errors) throws Exception
       {
           getPageConfig().setShowHeader(false);
           return new HtmlView("Delete file " + form.getName() + "?");
       }

       public boolean handlePost(AttachmentForm form, BindException errors) throws Exception
       {
           try
           {
               _closeView = AttachmentService.get().delete(getAttachmentParent(form), form.getName(), getUser());
           }
           catch (NotFoundException e)
           {
               _closeView = ExceptionUtil.getErrorView(HttpServletResponse.SC_NOT_FOUND, e.getMessage(), e, getViewContext().getRequest(), false, true);
           }
           return true;
       }

       public void validateCommand(AttachmentForm target, Errors errors)
       {
       }

       public ActionURL getSuccessURL(AttachmentForm attachmentForm)
       {
           return null;
       }

       public ModelAndView getSuccessView(AttachmentForm form)
       {
           getPageConfig().setTemplate(PageConfig.Template.None);
           return _closeView;
       }

       public ActionURL getFailURL(AttachmentForm attachmentForm, BindException errors)
       {
           return null;
       }
   }

   @RequiresSiteAdmin
   public class ShowAdminAction extends FormViewAction<FileContentForm>
   {
       public ShowAdminAction()
       {
       }

       public ShowAdminAction(ViewContext context)
       {
           setViewContext(context);
       }

       public ModelAndView getView(FileContentForm form, boolean reshow, BindException errors) throws Exception
       {
           FileContentService service = ServiceRegistry.get().getService(FileContentService.class);

           if (service != null)
           {
               File root = service.getFileRoot(getContainer());
               if (null == form.getRootPath() && null != root)
               {
                   String path = root.getPath();
                   try
                   {
                       NetworkDrive.ensureDrive(path);
                       path = FileUtil.getAbsoluteCaseSensitiveFile(root).getAbsolutePath();
                   }
                   catch (Exception e)
                   {
                       logger.error("Could not get canonical path for " + root.getPath() + ", using path as entered.", e);
                   }
                   form.setRootPath(path);
               }
           }
           return new JspView<>("/org/labkey/filecontent/view/configure.jsp", form, errors);
       }


       public void validateCommand(FileContentForm target, Errors errors)
       {
       }

       public boolean handlePost(FileContentForm fileContentForm, BindException errors) throws Exception
       {
           return false;
       }

       public ActionURL getSuccessURL(FileContentForm fileContentForm)
       {
           return null;
       }

       public NavTree appendNavTrail(NavTree root)
       {
           return (new BeginAction()).appendNavTrail(root)
                   .addChild("Administer File System Access");
       }
   }


   @RequiresSiteAdmin
   public class AddAttachmentDirectoryAction extends ShowAdminAction
   {
       public static final int MAX_NAME_LENGTH = 80;
       public static final int MAX_PATH_LENGTH = 255;
       public boolean handlePost(FileContentForm form, BindException errors) throws Exception
       {
           String name = StringUtils.trimToNull(form.getFileSetName());
           String path = StringUtils.trimToNull(form.getPath());
           FileContentService service = ServiceRegistry.get().getService(FileContentService.class);

           if (null == name)
              	errors.reject(SpringActionController.ERROR_MSG, "Please enter a label for the file set. ");
           else if (name.length() > MAX_NAME_LENGTH)
                errors.reject(SpringActionController.ERROR_MSG, "Name is too long, should be less than " + MAX_NAME_LENGTH +" characters.");
           else
           {
               AttachmentDirectory attDir = service.getRegisteredDirectory(getContainer(), name);
               if (null != attDir)
                   errors.reject(SpringActionController.ERROR_MSG, "A file set named "  + name + " already exists.");
           }
           if (null == path)
               errors.reject(SpringActionController.ERROR_MSG, "Please enter a full path to the file set.");
           else if (path.length() > MAX_PATH_LENGTH)
                errors.reject(SpringActionController.ERROR_MSG, "File path is too long, should be less than " + MAX_PATH_LENGTH + " characters.");

		   String message = "";
           if (errors.getErrorCount() == 0)
           {
               service.registerDirectory(getContainer(), name, path, false);
               message = "Directory successfully registered.";
               File dir = new File(path);
               if (!dir.exists())
                   message += " NOTE: Directory does not currently exist. An administrator will have to create it before it can be used.";
               form.setPath(null);
               form.setFileSetName(null);
           }
           File webRoot = null;

           if (service != null)
               webRoot = service.getFileRoot(getContainer());
           form.setRootPath(webRoot == null ? null : FileUtil.getAbsoluteCaseSensitiveFile(webRoot).getAbsolutePath());
           form.setMessage(StringUtils.trimToNull(message));
           setReshow(true);
           return errors.getErrorCount() == 0;
       }
   }


   @RequiresSiteAdmin
   public class DeleteAttachmentDirectoryAction extends ShowAdminAction
   {
       public boolean handlePost(FileContentForm form, BindException errors) throws Exception
       {
           String name = StringUtils.trimToNull(form.getFileSetName());
           FileContentService service = ServiceRegistry.get().getService(FileContentService.class);

           if (null == name)
           {
               errors.reject(SpringActionController.ERROR_MSG, "No name for fileset supplied.");
               return false;
           }
           AttachmentDirectory attDir = service.getRegisteredDirectory(getContainer(), name);
           if (null == attDir)
           {
               form.setMessage("Attachment directory named " + name + " not found");
           }
           else
           {
               service.unregisterDirectory(getContainer(), form.getFileSetName());
               form.setMessage("Directory was removed from list. Files were not deleted.");
               form.setPath(null);
               form.setFileSetName(null);
           }
           File webRoot = service.getFileRoot(getContainer());
           form.setRootPath(webRoot == null ? null : FileUtil.getAbsoluteCaseSensitiveFile(webRoot).getAbsolutePath());
           setReshow(true);

		   return true;
       }
   }

    static class NodeForm
    {
        private String _node;
        private String _rootContainer;
        private boolean _showOverridesOnly = false;
        private boolean _excludeWorkbooksAndTabs = false;

        public String getNode()
        {
            return _node;
        }

        public void setNode(String node)
        {
            _node = node;
        }

        public String getRootContainer()
        {
            return _rootContainer;
        }

        public void setRootContainer(String rootContainer)
        {
            _rootContainer = rootContainer;
        }

        public boolean isShowOverridesOnly()
        {
            return _showOverridesOnly;
        }

        public void setShowOverridesOnly(boolean showOverridesOnly)
        {
            _showOverridesOnly = showOverridesOnly;
        }

        public boolean isExcludeWorkbooksAndTabs()
        {
            return _excludeWorkbooksAndTabs;
        }

        public void setExcludeWorkbooksAndTabs(boolean excludeWorkbooksAndTabs)
        {
            _excludeWorkbooksAndTabs = excludeWorkbooksAndTabs;
        }
    }

    @RequiresPermissionClass(ReadPermission.class)
    public class FileContentSummaryAction extends FileTreeNodeAction
    {
        public ApiResponse execute(NodeForm form, BindException errors) throws Exception
        {
            Container c = ContainerManager.getForId(form.getNode());
            if (c == null)
                c = ContainerManager.getForId(form.getRootContainer());
            if (c == null)
                c = ContainerManager.getRoot();

            Set<Map<String, Object>> children = new LinkedHashSet<>();
            FileContentService svc = ServiceRegistry.get().getService(FileContentService.class);

            try {
                AttachmentDirectory root = svc.getMappedAttachmentDirectory(c, false);
                ActionURL browse = new ActionURL(BeginAction.class, c);

                if (root != null)
                {
                    boolean isDefault = svc.isUseDefaultRoot(c);
                    if (!isDefault || !form.isShowOverridesOnly())
                    {
                        ActionURL config = PageFlowUtil.urlProvider(AdminUrls.class).getProjectSettingsFileURL(c);
                        Map<String, Object> node = createFileSetNode("@files", root.getFileSystemDirectory());
                        node.put("default", svc.isUseDefaultRoot(c));
                        node.put("configureURL", config.getEncodedLocalURIString());
                        node.put("browseURL", browse.getEncodedLocalURIString());

                        children.add(node);
                    }
                }

                for (AttachmentDirectory fileSet : svc.getRegisteredDirectories(c))
                {
                    ActionURL config = new ActionURL(ShowAdminAction.class, c);
                    Map<String, Object> node =  createFileSetNode(fileSet.getName(), fileSet.getFileSystemDirectory());
                    node.put("configureURL", config.getEncodedLocalURIString());
                    node.put("browseURL", browse.getEncodedLocalURIString());

                    children.add(node);
                }

                PipeRoot pipeRoot = PipelineService.get().findPipelineRoot(c);
                if (pipeRoot != null)
                {
                    boolean isDefault = PipelineService.get().hasSiteDefaultRoot(c);
                    if (!isDefault || !form.isShowOverridesOnly())
                    {
                        ActionURL config = PageFlowUtil.urlProvider(PipelineUrls.class).urlSetup(c);
                        ActionURL pipelineBrowse = PageFlowUtil.urlProvider(PipelineUrls.class).urlBrowse(c, null);
                        Map<String, Object> node = createFileSetNode("@pipeline", pipeRoot.getRootPath());
                        node.put("default", isDefault );
                        node.put("configureURL", config.getEncodedLocalURIString());
                        node.put("browseURL", pipelineBrowse.getEncodedLocalURIString());

                        children.add(node);
                    }
                }
            }
            catch (MissingRootDirectoryException e){}
            catch (UnsetRootDirectoryException e){}

            for (Container child : c.getChildren())
            {
                if (form.isExcludeWorkbooksAndTabs() && child.isWorkbookOrTab())
                    continue;

                Map<String, Object> node = new HashMap<>();

                node.put("id", child.getId());
                node.put("name", child.getName());

                children.add(node);
            }
            return new ApiSimpleResponse("children", children);
        }
    }

    /**
     * Returns information for project file web part administrative information on a per project basis
     */
    @RequiresPermissionClass(ReadPermission.class)
    public class FileContentProjectSummaryAction extends FileTreeNodeAction
    {
        private static final String NODE_LABEL = "file web part";

        public ApiResponse execute(NodeForm form, BindException errors) throws Exception
        {
            Container c = ContainerManager.getForId(form.getNode());
            if (c == null)
                c = ContainerManager.getForId(form.getRootContainer());
            if (c == null)
                c = ContainerManager.getRoot();

            Set<Map<String, Object>> children = new LinkedHashSet<>();
            FileContentService svc = ServiceRegistry.get().getService(FileContentService.class);

            try
            {
                AttachmentDirectory root = svc.getMappedAttachmentDirectory(c, false);
                ActionURL browse = new ActionURL(BeginAction.class, c);

                if (root != null)
                {

                    Map<String, Object> node = createFileSetNode(NODE_LABEL, root.getFileSystemDirectory());

                    if (containsFileWebPart(c))
                    {
                        ActionURL config = PageFlowUtil.urlProvider(AdminUrls.class).getFolderManagementURL(c);
                        config.addParameter("tabId", "files");

                        node.put("configureURL", config.getEncodedLocalURIString());
                        node.put("browseURL", browse.getEncodedLocalURIString());
                    }
                    else
                    {
                        node.put("path", "web part not added");                        
                    }
                    children.add(node);
                }
            }
            catch (MissingRootDirectoryException e){}
            catch (UnsetRootDirectoryException e){}

            // include all child containers
            for (Container child : c.getChildren())
            {
                Map<String, Object> node = new HashMap<>();

                node.put("id", child.getId());
                node.put("name", child.getName());

                children.add(node);
            }
            return new ApiSimpleResponse("children", children);
        }

        private boolean containsFileWebPart(Container c)
        {
            SimpleFilter filter = SimpleFilter.createContainerFilter(c).addCondition(FieldKey.fromParts("name"), FilesWebPart.PART_NAME);

            return new TableSelector(Portal.getTableInfoPortalWebParts(), filter, null).exists();
        }
    }

    @RequiresPermissionClass(ReadPermission.class)
    public abstract class FileTreeNodeAction extends ApiAction<NodeForm>
    {
        @Override
        public ApiResponseWriter createResponseWriter() throws IOException
        {
            return new ApiJsonWriter(getViewContext().getResponse(), getContentTypeOverride())
            {
                @Override
                public void write(ApiResponse response) throws IOException
                {
                    // need to write out the json in a form that the ext tree loader expects
                    Map<String, ?> props = response.getProperties();
                    if (props.containsKey("children"))
                    {
                        JSONArray json = new JSONArray((Collection<Object>)props.get("children"));
                        getWriter().write(json.toString(4));
                    }
                    else
                        super.write(response);
                }
            };
        }

        protected Map<String, Object> createFileSetNode(String name, File dir)
        {
            Map<String, Object> node = new HashMap<>();
            if (dir != null)
            {
                node.put("name", name);
                node.put("path", dir.getPath());
                node.put("leaf", true);
            }
            return node;
        }
    }


    @RequiresPermissionClass(AdminPermission.class)
    public class DesignerAction extends SimpleViewAction<ReturnUrlForm>
    {
        @Override
        public ModelAndView getView(ReturnUrlForm form, BindException errors) throws Exception
        {
            FileContentService svc = ServiceRegistry.get().getService(FileContentService.class);
            String uri = svc.getDomainURI(getContainer());
            OntologyManager.ensureDomainDescriptor(uri, FileContentServiceImpl.PROPERTIES_DOMAIN, getContainer());
            Map<String, String> properties = new HashMap<>();

            properties.put("typeURI", uri);
            properties.put("domainName", FileContentServiceImpl.PROPERTIES_DOMAIN);
            properties.put(ActionURL.Param.returnUrl.name(), form.getReturnUrl()==null?null:form.getReturnUrl().getSource());
            properties.put(ActionURL.Param.cancelUrl.name(), form.getReturnUrl()==null?null:form.getReturnUrl().getSource());

            return new GWTView("org.labkey.filecontent.designer.FilePropertiesDesigner", properties);
        }

        @Override
        public NavTree appendNavTrail(NavTree root)
        {
            setHelpTopic("propertyFields");
            return root.addChild("File Properties Designer");
        }
    }

    // GWT Action
    @RequiresPermissionClass(AdminPermission.class)
    public class FilePropertiesServiceAction extends GWTServiceAction
    {
        protected BaseRemoteService createService()
        {
            return new FilePropertiesServiceImpl(getViewContext());
        }
    }

    public static class CustomFilePropsForm
    {
        private String _uri;

        public String getUri()
        {
            return _uri;
        }

        public void setUri(String uri)
        {
            _uri = uri;
        }
    }


    public static class FilePropsForm implements CustomApiForm
    {
        private Map<String,Object> _props;

        public void bindProperties(Map<String, Object> props)
        {
            _props = props;
        }

        public Map<String,Object> getProps()
        {
            return _props;
        }
    }

    @RequiresPermissionClass(InsertPermission.class)
    public class UpdateFilePropsAction extends MutatingApiAction<FilePropsForm>
    {
        private List<Map<String, Object>> _files;
        private DomainProperty[] _domainProps;

        public ApiResponse execute(FilePropsForm form, BindException errors) throws Exception
        {
            ApiSimpleResponse response = new ApiSimpleResponse();
            TableInfo ti = ExpSchema.TableType.Data.createTable(new ExpSchema(getUser(), getContainer()), ExpSchema.TableType.Data.toString());
            QueryUpdateService qus = ti.getUpdateService();

            try {
                qus.updateRows(getUser(), getContainer(), _files, null, null);
                response.put("success", true);
            }
            catch (QueryUpdateServiceException e)
            {
                response.put("success", false);
            }
            return response;
        }

        private static final String FILE_PROP_ERROR = "%s : %s";

        @Override
        public void validateForm(FilePropsForm form, Errors errors)
        {
            _files = parseFromJSON(form.getProps());

            FileContentService svc = ServiceRegistry.get().getService(FileContentService.class);
            String uri = svc.getDomainURI(getContainer());
            DomainDescriptor dd = OntologyManager.getDomainDescriptor(uri, getContainer());

            if (dd != null)
            {
                Domain domain = PropertyService.get().getDomain(dd.getDomainId());
                if (domain != null)
                {
                    ValidatorContext validatorCache = new ValidatorContext(getContainer(), getUser());
                    List<ValidationError> validationErrors = new ArrayList<>();
                    _domainProps = domain.getProperties();

                    for (Map<String, Object> fileProps : _files)
                    {
                        WebdavResource resource = getResource(String.valueOf(fileProps.get("id")));
                        if (resource != null && !resource.getActions(getUser()).isEmpty())
                        {
                            errors.reject(ERROR_MSG, String.format(FILE_PROP_ERROR, resource.getName(), "has been previously processed, properties cannot be edited"));
                            return;
                        }

                        String name = String.valueOf(fileProps.get("name"));
                        for (DomainProperty dp : _domainProps)
                        {
                            Object o = fileProps.get(dp.getName());
                            if (!validateProperty(dp, String.valueOf(o), validationErrors, validatorCache))
                            {
                                for (ValidationError ve : validationErrors)
                                    errors.reject(ERROR_MSG, String.format(FILE_PROP_ERROR, name, ve.getMessage()));
                                return;
                            }
                        }

                        // need this as a key for the query update service
                        try {
                            File canonicalFile = FileUtil.getAbsoluteCaseSensitiveFile(resource.getFile());
                            String url = canonicalFile.toURI().toURL().toString();

                            fileProps.put(ExpDataTable.Column.DataFileUrl.name(), url);
                        }
                        catch (MalformedURLException e)
                        {
                            errors.reject(ERROR_MSG, String.format(FILE_PROP_ERROR, name, "Unable to create the DataFileUrl"));
                        }
                    }
                }
            }
        }

        private WebdavResource getResource(String uri)
        {
            Path path = Path.decode(uri);

            if (!path.startsWith(WebdavService.getPath()) && path.contains(WebdavService.getPath().getName()))
            {
                String newPath = path.toString();
                int idx = newPath.indexOf(WebdavService.getPath().toString());

                if (idx != -1)
                {
                    newPath = newPath.substring(idx);
                    path = Path.parse(newPath);
                }
            }
            return WebdavService.get().getResolver().lookup(path);
        }

        private List<Map<String, Object>> parseFromJSON(Map<String, Object> props)
        {
            List<Map<String, Object>> files = new ArrayList<>();

            if (props.containsKey("files"))
            {
                Object fileObj = props.get("files");
                if (fileObj instanceof JSONArray)
                {
                    JSONArray jarray = (JSONArray)fileObj;

                    for (int i=0; i < jarray.length(); i++)
                    {
                        Map<String, Object> fileProps = new HashMap<>();

                        JSONObject jobj = jarray.getJSONObject(i);
                        if (jobj != null)
                        {
                            for (Map.Entry<String, Object> entry : jobj.entrySet())
                            {
                                if (entry.getValue() instanceof String)
                                    fileProps.put(entry.getKey(), StringUtils.trimToNull((String)entry.getValue()));
                                else
                                    fileProps.put(entry.getKey(), entry.getValue());
                            }
                        }
                        files.add(fileProps);
                    }
                }
            }
            return files;
        }

        private boolean validateProperty(DomainProperty prop, Object value, List<ValidationError> errors, ValidatorContext validatorCache)
        {
            // Don't validate null values, #15683
            if (null == value)
                return true;

            boolean ret = true;

            for (IPropertyValidator validator : prop.getValidators())
            {
                if (!validator.validate(prop.getPropertyDescriptor(), value, errors, validatorCache)) ret = false;
            }
            return ret;
        }
    }

    @RequiresPermissionClass(InsertPermission.class)
    public class SaveCustomFilePropsAction extends ExtFormAction<CustomFilePropsForm>
    {
        WebdavResource _resource;

        @Override
        public void validateForm(CustomFilePropsForm form, Errors errors)
        {
            Path path = Path.decode(form.getUri());

            if (!path.startsWith(WebdavService.getPath()) && path.contains(WebdavService.getPath().getName()))
            {
                String newPath = path.toString();
                int idx = newPath.indexOf(WebdavService.getPath().toString());

                if (idx != -1)
                {
                    newPath = newPath.substring(idx);
                    path = Path.parse(newPath);
                }
            }
            _resource = WebdavService.get().getResolver().lookup(path);

            if (_resource != null)
            {
                FileContentService svc = ServiceRegistry.get().getService(FileContentService.class);
                String uri = svc.getDomainURI(getContainer());
                DomainDescriptor dd = OntologyManager.getDomainDescriptor(uri, getContainer());

                if (dd != null)
                {
                    Domain domain = PropertyService.get().getDomain(dd.getDomainId());
                    if (domain != null)
                    {
                        ValidatorContext validatorCache = new ValidatorContext(getContainer(), getUser());
                        List<ValidationError> validationErrors = new ArrayList<>();

                        for (DomainProperty prop : domain.getProperties())
                        {
                            Object o = getViewContext().get(prop.getName());
                            if (o != null && !StringUtils.isBlank(String.valueOf(o)))
                            {
                                if (!validateProperty(prop, StringUtils.trimToNull(String.valueOf(o)), validationErrors, validatorCache))
                                {
                                    for (ValidationError ve : validationErrors)
                                        errors.reject(ERROR_MSG, ve.getMessage());
                                    break;
                                }
                            }
                        }
                    }
                }
            }
            else
                errors.reject("Failed trying to resolve the resource URI.");
        }

        private boolean validateProperty(DomainProperty prop, Object value, List<ValidationError> errors, ValidatorContext validatorCache)
        {
            boolean ret = true;

            //check for isRequired
            if (prop.isRequired() && value == null)
            {
                errors.add(new PropertyValidationError("The field '" + prop.getName() + "' is required.", prop.getName()));
                return false;
            }

            // Don't validate null values, #15683
            if (null == value)
                return ret;

            for (IPropertyValidator validator : prop.getValidators())
            {
                if (!validator.validate(prop.getPropertyDescriptor(), value, errors, validatorCache)) ret = false;
            }
            return ret;
        }

        public ApiResponse execute(CustomFilePropsForm form, BindException errors) throws Exception
        {
            FileContentService svc = ServiceRegistry.get().getService(FileContentService.class);
            ExpData data = svc.getDataObject(_resource, getContainer());
            ApiSimpleResponse response = new ApiSimpleResponse();

            if (data != null)
            {
                String uri = svc.getDomainURI(getContainer());
                DomainDescriptor dd = OntologyManager.getDomainDescriptor(uri, getContainer());

                if (dd != null)
                {
                    Domain domain = PropertyService.get().getDomain(dd.getDomainId());
                    if (domain != null)
                    {
                        try {
                            for (DomainProperty prop : domain.getProperties())
                            {
                                Object o = getViewContext().get(prop.getName());
                                if (o != null && !StringUtils.isBlank(String.valueOf(o)))
                                {
                                    data.setProperty(getUser(), prop.getPropertyDescriptor(), o);
                                }
                            }
                            response.put("success", true);
                        }
                        catch (ValidationException e){}
                    }
                }
            }
            else
                response.put("success", false);

            return response;
        }
    }

    public static class ResetType
    {
        private String _type;

        public String getType()
        {
            return _type;
        }

        public void setType(String type)
        {
            _type = type;
        }
    }

    @RequiresPermissionClass(AdminPermission.class)
    public class ResetFileOptionsAction extends MutatingApiAction<ResetType>
    {
        public ApiResponse execute(ResetType form, BindException errors) throws Exception
        {
            FileContentService svc = ServiceRegistry.get().getService(FileContentService.class);
            FilesAdminOptions options = svc.getAdminOptions(getContainer());

            if (form.getType().equalsIgnoreCase("tbar"))
            {
                options.setTbarConfig(Collections.<FilesTbarBtnOption>emptyList());
                options.setGridConfig(null);
            }
            else if (form.getType().equalsIgnoreCase("actions"))
                options.setPipelineConfig(Collections.<PipelineActionConfig>emptyList());

            svc.setAdminOptions(getContainer(), options);

            return new ApiSimpleResponse("success", true);
        }
    }

    @RequiresPermissionClass(ReadPermission.class)
    public class GetEmailPrefAction extends ApiAction<Object>
    {
        @Override
        public ApiResponse execute(Object o, BindException errors) throws Exception
        {
            ApiSimpleResponse response =  new ApiSimpleResponse();

            MessageConfigService.ConfigTypeProvider provider = MessageConfigService.getInstance().getConfigType(FileEmailConfig.TYPE);
            MessageConfigService.UserPreference pref = provider.getPreference(getContainer(), getUser(), getContainer().getId());

            String prefWithDefault = EmailService.get().getEmailPref(getUser(), getContainer(),
                    new FileContentEmailPref(), new FileContentDefaultEmailPref());

            response.put("emailPref", pref != null ? pref.getEmailOptionId() : "-1");
            response.put("emailPrefDefault", prefWithDefault);
            response.put("success", true);

            return response;
        }
    }

    @RequiresPermissionClass(ReadPermission.class)
    public class SetEmailPrefAction extends MutatingApiAction<EmailPrefForm>
    {
        @Override
        public ApiResponse execute(EmailPrefForm form, BindException errors) throws Exception
        {
            ApiSimpleResponse response =  new ApiSimpleResponse();

            MessageConfigService.ConfigTypeProvider provider = MessageConfigService.getInstance().getConfigType(FileEmailConfig.TYPE);
            provider.savePreference(getUser(), getContainer(), getUser(), form.getEmailPref(), getContainer().getId());
            response.put("success", true);

            return response;
        }
    }

    @RequiresPermissionClass(AdminPermission.class)
    public class SetDefaultEmailPrefAction extends MutatingApiAction<EmailPrefForm>
    {
        @Override
        public ApiResponse execute(EmailPrefForm form, BindException errors) throws Exception
        {
            ApiSimpleResponse response =  new ApiSimpleResponse();
            StringBuilder message = new StringBuilder("The current default has been updated to: ");

            //save the default settings
            EmailService.get().setDefaultEmailPref(getContainer(), new FileContentDefaultEmailPref(), String.valueOf(form.getEmailPref()));

            for (MessageConfigService.NotificationOption option : MessageConfigService.getInstance().getConfigType(FileEmailConfig.TYPE).getOptions())
            {
                if (option.getEmailOptionId() == form.getEmailPref())
                {
                    message.append(option.getEmailOption());
                    break;
                }
            }
            response.put("success", true);
            response.put("message", message.toString());          

            return response;
        }
    }

    @RequiresPermissionClass(AdminPermission.class)
    public class ShowFilesHistoryAction extends SimpleViewAction
    {
        public ModelAndView getView(Object o, BindException errors) throws Exception
        {
            if (AuditLogService.enableHardTableLogging())
            {
                UserSchema schema = AuditLogService.getAuditLogSchema(getUser(), getContainer());

                if (schema != null)
                {
                    QuerySettings settings = new QuerySettings(getViewContext(), QueryView.DATAREGIONNAME_DEFAULT);
                    settings.setQueryName(FileSystemAuditProvider.EVENT_TYPE);
                    return schema.createView(getViewContext(), settings, errors);
                }
                return null;
            }
            else
                return FileSystemAuditViewFactory.getInstance().createFileContentView(getViewContext());
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return root.addChild("File History");
        }
    }

    @RequiresPermissionClass(ReadPermission.class)
    public class FileEmailPreferenceAction extends SimpleViewAction
    {
        public ModelAndView getView(Object o, BindException errors) throws Exception
        {
            return new JspView("/org/labkey/filecontent/view/configureEmail.jsp");
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return root.addChild("Email Notification Preferences");
        }
    }

    @RequiresSiteAdmin
    public class SendShortDigestAction extends SimpleViewAction
    {
        public ModelAndView getView(Object o, BindException errors) throws Exception
        {
            ShortMessageDigest.getInstance().sendMessageDigest();

            return new HtmlView("15 Minute digest sent");
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return new BeginAction().appendNavTrail(root).addChild("Send mail digest");
        }
    }

    public static class EmailPrefForm
    {
        int _emailPref;

        public int getEmailPref()
        {
            return _emailPref;
        }

        public void setEmailPref(int emailPref)
        {
            _emailPref = emailPref;
        }
    }

    public static class FileContentForm
    {
        private String rootPath;
        private String rootOffset;
        private String message;
        private String fileSetName;
        private String path;

        public String getRootPath()
        {
            return rootPath;
        }

        public void setRootPath(String rootPath)
        {
            this.rootPath = rootPath;
        }

        public String getMessage()
        {
            return message;
        }

        public void setMessage(String message)
        {
            this.message = message;
        }

        public String getFileSetName()
        {
            return fileSetName;
        }

        public void setFileSetName(String fileSetName)
        {
            this.fileSetName = fileSetName;
        }

        public void setPath(String path)
        {
            this.path = path;
        }

        public String getPath()
        {
            return path;
        }

        public String getRootOffset()
        {
            return rootOffset;
        }

        public void setRootOffset(String rootOffset)
        {
            this.rootOffset = rootOffset;
        }
    }

   public static class SendFileForm
   {
       private String fileName;
       private String renderAs;
       private String fileSet;

       public String getFileName()
       {
           return fileName;
       }

       public void setFileName(String fileName)
       {
           this.fileName = fileName;
       }

       public String getRenderAs()
       {
           return renderAs;
       }

       public void setRenderAs(String renderAs)
       {
           this.renderAs = renderAs;
       }

       public RenderStyle getRenderStyle()
       {
           if (null == renderAs)
               return FileContentController.RenderStyle.PAGE;

           //Will throw illegal argument exception for other values...
           return FileContentController.RenderStyle.valueOf(renderAs.toUpperCase());
       }

       public String getFileSet()
       {
           return fileSet;
       }

       public void setFileSet(String fileSet)
       {
           this.fileSet = fileSet;
       }

       public ActionURL getDownloadURL(Container c)
       {
           ActionURL url = new ActionURL(SendFileAction.class, c);
           url.addParameter("fileName", getFileName());
           url.addParameter("renderAs", RenderStyle.ATTACHMENT.name());
           if (!StringUtils.isEmpty(getFileSet()))
               url.addParameter("fileSet",getFileSet());
           return url;
       }
   }

    private static class IFrameView extends JspView<String>
    {
        public IFrameView(String url)
        {
			super(FileContentController.class, "view/iframe.jsp", url);
        }
    }


    private static class ImgView extends HtmlView
    {
        public ImgView(String url)
        {
            super("\n<img src=\"" + PageFlowUtil.filter(url) + "\">");
        }
    }


    void addError(BindException errors, String msg)
    {
        errors.addError(new ObjectError("form", new String[] {"Error"}, new Object[]{msg}, msg));
    }


    private AttachmentDirectory getAttachmentParent(AttachmentForm form) throws NotFoundException
    {
        AttachmentDirectory attachmentParent;
        FileContentService svc = ServiceRegistry.get().getService(FileContentService.class);
        try
        {
            if (null == form.getEntityId() || form.getEntityId().equals(getContainer().getId()))
                attachmentParent = svc.getMappedAttachmentDirectory(getContainer(), true);
            else
                attachmentParent = svc.getRegisteredDirectoryFromEntityId(getContainer(), form.getEntityId());
        }
        catch (UnsetRootDirectoryException e)
        {
            throw new NotFoundException("The web root for this project is not set. Please contact an administrator.", e);
        }
        catch (MissingRootDirectoryException e)
        {
            throw new NotFoundException("The web root for this project is set to a non-existent directory. Please contact an administrator", e);
        }

        boolean exists = false;
        try
        {
            exists = attachmentParent.getFileSystemDirectory().exists();
        }
        catch (MissingRootDirectoryException ex)
        {
            /* */
        }
        if (!exists)
            throw new NotFoundException("Directory for saving file does not exist. Please contact an administrator.");

        return attachmentParent;
    }

    static MimeMap mimeMap = new MimeMap();

    public static RenderStyle defaultRenderStyle(String name)
    {
        if (mimeMap.isInlineImageFor(name))
            return RenderStyle.IMAGE;
        if (mimeMap.isOfficeDocumentFor(name))
            return RenderStyle.ATTACHMENT;
        if (name.endsWith(".body"))
            return RenderStyle.INCLUDE;
        String mimeType = StringUtils.defaultString(mimeMap.getContentTypeFor(name),"");
        if (mimeType.equals("application/pdf"))
            return RenderStyle.PAGE;
        if (mimeType.equals("text/html"))
            return RenderStyle.INCLUDE;
        if (mimeType.startsWith("text/"))
            return RenderStyle.TEXT;
        return RenderStyle.PAGE;
    }
}
