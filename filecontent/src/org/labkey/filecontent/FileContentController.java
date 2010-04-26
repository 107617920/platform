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

package org.labkey.filecontent;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.json.JSONArray;
import org.json.JSONObject;
import org.labkey.api.action.*;
import org.labkey.api.admin.AdminUrls;
import org.labkey.api.attachments.AttachmentDirectory;
import org.labkey.api.attachments.AttachmentForm;
import org.labkey.api.attachments.AttachmentService;
import org.labkey.api.attachments.SpringAttachmentFile;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.exp.DomainDescriptor;
import org.labkey.api.exp.OntologyManager;
import org.labkey.api.exp.api.ExpData;
import org.labkey.api.exp.property.*;
import org.labkey.api.files.*;
import org.labkey.api.files.view.FilesWebPart;
import org.labkey.api.gwt.server.BaseRemoteService;
import org.labkey.api.notification.EmailService;
import org.labkey.api.pipeline.PipeRoot;
import org.labkey.api.pipeline.PipelineService;
import org.labkey.api.pipeline.PipelineUrls;
import org.labkey.api.query.PropertyValidationError;
import org.labkey.api.query.ValidationError;
import org.labkey.api.query.ValidationException;
import org.labkey.api.security.RequiresPermissionClass;
import org.labkey.api.security.RequiresSiteAdmin;
import org.labkey.api.security.UserManager;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.security.permissions.DeletePermission;
import org.labkey.api.security.permissions.InsertPermission;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.services.ServiceRegistry;
import org.labkey.api.util.*;
import org.labkey.api.view.*;
import org.labkey.api.view.template.PageConfig;
import org.labkey.api.webdav.FileSystemResource;
import org.labkey.api.webdav.WebdavResource;
import org.labkey.api.webdav.WebdavService;
import org.springframework.validation.BindException;
import org.springframework.validation.Errors;
import org.springframework.validation.ObjectError;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.http.HttpServletResponse;
import java.io.*;
import java.util.*;


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

   static DefaultActionResolver _actionResolver = new DefaultActionResolver(FileContentController.class);

   public FileContentController() throws Exception
   {
       super();
       setActionResolver(_actionResolver);
   }


/*   // UNDONE: need better way to set right pane
   protected ModelAndView getTemplate(ViewContext context, ModelAndView mv, Controller action, PageConfig page)
   {
       ModelAndView t = super.getTemplate(context, mv, action, page);
       if (action instanceof BeginAction && t instanceof HomeTemplate)
       {
           HomeTemplate template = (HomeTemplate)t;
           template.setView("right", new FileSetsWebPart(getContainer()));
       }
       return t;
   }
*/

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
                p = svc.getRegisteredDirectory(getContainer(), form.getFileSet());

            if (null == p)
                throw new NotFoundException();

            File dir = p.getFileSystemDirectory();
            if (null == dir)
            {
                if (getUser().isAdministrator())
                    return HttpView.redirect("showAdmin.view");
                else
                    throw new NotFoundException();
            }

            Path filePath = Path.decode(form.getFileName());
            Path path;

            // support both legacy and newer formats, new URLs looks like webdav URLs, while older formats assume files
            // are only served out of the root
            if (filePath.contains(FileContentService.FILES_LINK) || filePath.contains(FileContentService.FILE_SETS_LINK) ||
                    filePath.contains(FileContentService.PIPELINE_LINK))
            {
                path = WebdavService.getPath().append(getContainer().getParsedPath()).append(filePath);
            }
            else
                path = WebdavService.getPath().append(getContainer().getParsedPath()).append(FileContentService.FILES_LINK).append(filePath);

            _resource = WebdavService.get().getResolver().lookup(path);

            if (_resource == null || !_resource.isFile())
                throw new NotFoundException();

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

                    PageFlowUtil.streamFile(getViewContext().getResponse(),
                            Collections.singletonMap("Content-Type",_resource.getContentType()),
                            _resource.getName(),
                            _resource.getInputStream(getUser()),
                            RenderStyle.ATTACHMENT==style);
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
                            InputStream fis = _resource.getInputStream(getUser());
                            try
                            {
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
                            try
                            {
                                // UNDONE stream html filter
                                String fileContents = PageFlowUtil.getStreamContentsAsString(_resource.getInputStream(getUser()));
                                String html = PageFlowUtil.filter(fileContents, true, true);
                                out.write(html);
                            }
                            catch (OutOfMemoryError x)
                            {
                                out.write("<span class='labkey-error'>file is too long: " + PageFlowUtil.filter(_resource.getName()) + "</span>");
                            }
                        }
                    };
                    webPart.setTitle(_resource.getName());
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
            part.setFrame(WebPartView.FrameType.NONE);
            part.setWide(true);
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

           if (HttpView.currentContext().getUser().isAdministrator())
               out.write("<br>[<a href='showAdmin.view'>Configure</a>]");
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
           if (fileMap.size() > 0 && !UserManager.mayWriteScript(getUser()))
           {
               for (MultipartFile formFile : fileMap.values())
               {
                   String contentType = new MimeMap().getContentTypeFor(formFile.getName());
                   if (formFile.getContentType().contains("html") || (null != contentType && contentType.contains("html")))
                   {
                       //This relies on storing whole file in memory. Generally OK for text files like this.
                       String html = new String(formFile.getBytes(),"UTF-8");
                       List<String> validateErrors = new ArrayList<String>();
                       List<String> safetyWarnings = new ArrayList<String>();
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
               _closeView = AttachmentService.get().add(getUser(), getAttachmentParent(form), SpringAttachmentFile.createList(fileMap));
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
           getPageConfig().setTitle("Delete File?");
           return new HtmlView("Delete file " + form.getName() + "?");
           //return AttachmentService.get().getConfirmDeleteView(attachmentParent, form);
       }

       public boolean handlePost(AttachmentForm form, BindException errors) throws Exception
       {
           try
           {
               _closeView = AttachmentService.get().delete(getUser(), getAttachmentParent(form), form.getName());
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
                       path = root.getCanonicalPath();
                   }
                   catch (Exception e)
                   {
                       logger.error("Could not get canonical path for " + root.getPath() + ", using path as entered.", e);
                   }
                   form.setRootPath(path);
               }
           }
           return new JspView<FileContentForm>("/org/labkey/filecontent/view/configure.jsp", form, errors);
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
               webRoot = service.getFileRoot(getContainer().getProject());
           form.setRootPath(webRoot == null ? null : webRoot.getCanonicalPath());
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
           File webRoot = service.getFileRoot(getContainer().getProject());
           form.setRootPath(webRoot == null ? null : webRoot.getCanonicalPath());
           setReshow(true);

		   return true;
       }
   }

    static class NodeForm
    {
        private String _node;

        public String getNode()
        {
            return _node;
        }

        public void setNode(String node)
        {
            _node = node;
        }
    }

    @RequiresPermissionClass(ReadPermission.class)
    public class FileContentSummaryAction extends ApiAction<NodeForm>
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
                    Map<String, Object> props = response.getProperties();
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

        public ApiResponse execute(NodeForm form, BindException errors) throws Exception
        {
            String container = form.getNode();

            Container c = ContainerManager.getForId(container);
            if (c == null)
                c = ContainerManager.getRoot();

            Set<Map<String, Object>> children = new LinkedHashSet<Map<String, Object>>();
            FileContentService svc = ServiceRegistry.get().getService(FileContentService.class);

            try {
                AttachmentDirectory root = svc.getMappedAttachmentDirectory(c, false);
                ActionURL browse = new ActionURL(BeginAction.class, c);

                if (root != null)
                {
                    ActionURL config = PageFlowUtil.urlProvider(AdminUrls.class).getProjectSettingsFileURL(c);
                    Map<String, Object> node = createFileSetNode("@files", root.getFileSystemDirectory());
                    node.put("default", svc.isUseDefaultRoot(c));
                    node.put("configureURL", config.getEncodedLocalURIString());
                    node.put("browseURL", browse.getEncodedLocalURIString());

                    children.add(node);
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
                    ActionURL config = PageFlowUtil.urlProvider(PipelineUrls.class).urlSetup(c);
                    ActionURL pipelineBrowse = PageFlowUtil.urlProvider(PipelineUrls.class).urlBrowse(c, null);
                    Map<String, Object> node = createFileSetNode("@pipeline", pipeRoot.getRootPath());
                    node.put("default", PipelineService.get().hasSiteDefaultRoot(c));
                    node.put("configureURL", config.getEncodedLocalURIString());
                    node.put("browseURL", pipelineBrowse.getEncodedLocalURIString());

                    children.add(node);
                }
            }
            catch (MissingRootDirectoryException e){}
            catch (UnsetRootDirectoryException e){}

            for (Container child : c.getChildren())
            {
                Map<String, Object> node = new HashMap<String, Object>();

                node.put("id", child.getId());
                node.put("name", child.getName());
                node.put("uiProvider", "col");

                children.add(node);
            }
            return new ApiSimpleResponse("children", children);
        }

        private Map<String, Object> createFileSetNode(String name, File dir)
        {
            Map<String, Object> node = new HashMap<String, Object>();
            if (dir != null)
            {
                node.put("name", name);
                node.put("path", dir.getPath());
                node.put("leaf", true);
                node.put("uiProvider", "col");
            }
            return node;
        }
    }

    public static class DesignerForm
    {

    }

    @RequiresPermissionClass(AdminPermission.class)
    public class DesignerAction extends SimpleViewAction<DesignerForm>
    {
        @Override
        public ModelAndView getView(DesignerForm designerForm, BindException errors) throws Exception
        {
            FileContentService svc = ServiceRegistry.get().getService(FileContentService.class);
            Map<String, String> properties = new HashMap<String, String>();

            properties.put("typeURI", svc.getDomainURI(getContainer(), FileContentService.TYPE_PROPERTIES));
            properties.put("domainName", FileContentServiceImpl.PROPERTIES_DOMAIN);
            
            return new GWTView("org.labkey.filecontent.designer.FilePropertiesDesigner", properties);
        }

        @Override
        public NavTree appendNavTrail(NavTree root)
        {
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
/*
            Container container = getViewContext().getContainer();
            User user = getViewContext().getUser();

            FilesAdminOptions options = FilesAdminOptions.fromJSON(container, form.getProps());

            FileContentService svc = ServiceRegistry.get().getService(FileContentService.class);
            svc.setAdminOptions(getContainer(), options);
*/
            ApiSimpleResponse response = new ApiSimpleResponse();

            for (Map<String, Object> fileProps : _files)
            {
                String uri = String.valueOf(fileProps.get("id"));

                if (!StringUtils.isEmpty(uri))
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
                    WebdavResource resource = WebdavService.get().getResolver().lookup(path);
                    if (resource != null)
                    {
                        ExpData data = FileContentServiceImpl.getDataObject(resource, getContainer(), getUser(), true);
                        if (data != null)
                        {
                            try {
                                for (DomainProperty prop : _domainProps)
                                {
                                    Object o = fileProps.get(prop.getName());
                                    if (o != null && !StringUtils.isBlank(String.valueOf(o)))
                                    {
                                        data.setProperty(getUser(), prop.getPropertyDescriptor(), o);
                                    }
                                }
                                response.put("success", true);
                            }
                            catch (ValidationException e){}
                        }
                        else
                            response.put("success", false);

                    }
                }
            }
            return response;
        }

        private static final String FILE_PROP_ERROR = "%s : %s";

        @Override
        public void validateForm(FilePropsForm form, Errors errors)
        {
            _files = parseFromJSON(form.getProps());

            FileContentService svc = ServiceRegistry.get().getService(FileContentService.class);
            String uri = svc.getDomainURI(getContainer(), FileContentService.TYPE_PROPERTIES);
            DomainDescriptor dd = OntologyManager.getDomainDescriptor(uri, getContainer());

            if (dd != null)
            {
                Domain domain = PropertyService.get().getDomain(dd.getDomainId());
                if (domain != null)
                {
                    ValidatorContext validatorCache = new ValidatorContext(getContainer(), getUser());
                    List<ValidationError> validationErrors = new ArrayList<ValidationError>();
                    _domainProps = domain.getProperties();

                    for (Map<String, Object> fileProps : _files)
                    {
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
                    }
                }
            }
        }

        private List<Map<String, Object>> parseFromJSON(Map<String, Object> props)
        {
            List<Map<String, Object>> files = new ArrayList<Map<String, Object>>();

            if (props.containsKey("files"))
            {
                Object fileObj = props.get("files");
                if (fileObj instanceof JSONArray)
                {
                    JSONArray jarray = (JSONArray)fileObj;

                    for (int i=0; i < jarray.length(); i++)
                    {
                        Map<String, Object> fileProps = new HashMap<String, Object>();

                        JSONObject jobj = jarray.getJSONObject(i);
                        if (jobj != null)
                        {
                            for (Map.Entry<String, Object> entry : jobj.entrySet())
                            {
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
                String uri = svc.getDomainURI(getContainer(), FileContentService.TYPE_PROPERTIES);
                DomainDescriptor dd = OntologyManager.getDomainDescriptor(uri, getContainer());

                if (dd != null)
                {
                    Domain domain = PropertyService.get().getDomain(dd.getDomainId());
                    if (domain != null)
                    {
                        ValidatorContext validatorCache = new ValidatorContext(getContainer(), getUser());
                        List<ValidationError> validationErrors = new ArrayList<ValidationError>();

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
                String uri = svc.getDomainURI(getContainer(), FileContentService.TYPE_PROPERTIES);
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

    @RequiresPermissionClass(AdminPermission.class)
    public class ResetFilesToolbarOptionsAction extends MutatingApiAction<Object>
    {
        public ApiResponse execute(Object form, BindException errors) throws Exception
        {
            FileContentService svc = ServiceRegistry.get().getService(FileContentService.class);
            FilesAdminOptions options = svc.getAdminOptions(getContainer());

            options.setTbarConfig(Collections.<FilesTbarBtnOption>emptyList());
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
            String pref = EmailService.get().getEmailPref(getUser(), getContainer(), new FileContentEmailPref());
            String prefWithDefault = EmailService.get().getEmailPref(getUser(), getContainer(),
                    new FileContentEmailPref(), new FileContentDefaultEmailPref());

            response.put("emailPref", pref);
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

            EmailService.get().setEmailPref(getUser(), getContainer(), new FileContentEmailPref(), String.valueOf(form.getEmailPref()));
            response.put("success", true);

            return response;
        }
    }

    /**
     * Returns group default email pref settings.
     */
    @RequiresPermissionClass(AdminPermission.class)
    public class GetDefaultEmailPrefAction extends ApiAction<Object>
    {
        @Override
        public ApiResponse execute(Object o, BindException errors) throws Exception
        {
            ApiSimpleResponse response =  new ApiSimpleResponse();
            String pref = EmailService.get().getDefaultEmailPref(getContainer(), new FileContentDefaultEmailPref());

            response.put("emailPref", pref);
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

            EmailService.get().setDefaultEmailPref(getContainer(), new FileContentDefaultEmailPref(), String.valueOf(form.getEmailPref()));
            response.put("success", true);

            return response;
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
        if (name.endsWith(".body"))
            return RenderStyle.INCLUDE;
        String mimeType = StringUtils.defaultString(mimeMap.getContentTypeFor(name),"");
        if (mimeType.equals("text/html"))
            return RenderStyle.INCLUDE;
        if (mimeType.startsWith("text/"))
            return RenderStyle.TEXT;
        return RenderStyle.PAGE;
    }
}
