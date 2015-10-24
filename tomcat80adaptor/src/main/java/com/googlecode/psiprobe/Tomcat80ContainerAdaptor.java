/*
 * Licensed under the GPL License. You may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 * http://www.gnu.org/licenses/old-licenses/gpl-2.0.html
 * 
 * THIS PACKAGE IS PROVIDED "AS IS" AND WITHOUT ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING,
 * WITHOUT LIMITATION, THE IMPLIED WARRANTIES OF MERCHANTIBILITY AND FITNESS FOR A PARTICULAR
 * PURPOSE.
 */

package com.googlecode.psiprobe;

import com.googlecode.psiprobe.model.ApplicationParam;
import com.googlecode.psiprobe.model.ApplicationResource;
import com.googlecode.psiprobe.model.FilterInfo;
import com.googlecode.psiprobe.model.FilterMapping;

import org.apache.catalina.Container;
import org.apache.catalina.Context;
import org.apache.catalina.Host;
import org.apache.catalina.Valve;
import org.apache.catalina.WebResource;
import org.apache.catalina.Wrapper;
import org.apache.catalina.deploy.NamingResourcesImpl;
import org.apache.commons.modeler.Registry;
import org.apache.jasper.JspCompilationContext;
import org.apache.jasper.Options;
import org.apache.jasper.compiler.JspRuntimeContext;
import org.apache.naming.ContextAccessController;
import org.apache.naming.ContextBindings;
import org.apache.tomcat.util.descriptor.web.ApplicationParameter;
import org.apache.tomcat.util.descriptor.web.ContextResource;
import org.apache.tomcat.util.descriptor.web.ContextResourceLink;
import org.apache.tomcat.util.descriptor.web.FilterDef;
import org.apache.tomcat.util.descriptor.web.FilterMap;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.management.MBeanServer;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;
import javax.naming.NamingException;
import javax.servlet.ServletContext;

/**
 * The Class Tomcat80ContainerAdaptor.
 *
 * @author Vlad Ilyushchenko
 * @author Mark Lewis
 * @author Andre Sollie
 */
public class Tomcat80ContainerAdaptor extends AbstractTomcatContainer {

  /** The host. */
  private Host host;
  
  /** The deployer o name. */
  private ObjectName deployerOName;
  
  /** The mbean server. */
  private MBeanServer mbeanServer;
  
  /** The valve. */
  private final Valve valve = new Tomcat80AgentValve();

  /* (non-Javadoc)
   * @see com.googlecode.psiprobe.TomcatContainer#setWrapper(org.apache.catalina.Wrapper)
   */
  @Override
  public void setWrapper(Wrapper wrapper) {
    if (wrapper != null) {
      host = (Host) wrapper.getParent().getParent();
      try {
        deployerOName =
            new ObjectName(host.getParent().getName() + ":type=Deployer,host=" + host.getName());
      } catch (MalformedObjectNameException e) {
        // do nothing here
      }
      host.getPipeline().addValve(valve);
      mbeanServer = Registry.getRegistry(null, null).getMBeanServer();
    } else if (host != null) {
      host.getPipeline().removeValve(valve);
    }
  }

  /* (non-Javadoc)
   * @see com.googlecode.psiprobe.TomcatContainer#canBoundTo(java.lang.String)
   */
  @Override
  public boolean canBoundTo(String binding) {
    boolean canBind = false;
    if (binding != null) {
      canBind |= binding.startsWith("Apache Tomcat/8.0");
      canBind |= binding.startsWith("Apache Tomcat (TomEE)/8.0");
      canBind |= (binding.startsWith("Pivotal tc") && binding.contains("/8.0"));
    }
    return canBind;
  }

  /* (non-Javadoc)
   * @see com.googlecode.psiprobe.AbstractTomcatContainer#findContextInternal(java.lang.String)
   */
  @Override
  protected Context findContextInternal(String name) {
    return (Context) host.findChild(name);
  }

  /* (non-Javadoc)
   * @see com.googlecode.psiprobe.TomcatContainer#findContexts()
   */
  @Override
  public List<Context> findContexts() {
    ArrayList<Context> results = new ArrayList<Context>();
    for (Container child : host.findChildren()) {
      if (child instanceof Context) {
        results.add((Context) child);
      }
    }
    return results;
  }

  /* (non-Javadoc)
   * @see com.googlecode.psiprobe.AbstractTomcatContainer#checkChanges(java.lang.String)
   */
  @Override
  protected void checkChanges(String name) throws Exception {
    Boolean result =
        (Boolean) mbeanServer.invoke(deployerOName, "isServiced", new String[] {name},
            new String[] {"java.lang.String"});
    if (!result) {
      mbeanServer.invoke(deployerOName, "addServiced", new String[] {name},
          new String[] {"java.lang.String"});
      try {
        mbeanServer.invoke(deployerOName, "check", new String[] {name},
            new String[] {"java.lang.String"});
      } finally {
        mbeanServer.invoke(deployerOName, "removeServiced", new String[] {name},
            new String[] {"java.lang.String"});
      }
    }
  }

  /* (non-Javadoc)
   * @see com.googlecode.psiprobe.TomcatContainer#getAppBase()
   */
  @Override
  public File getAppBase() {
    File base = new File(host.getAppBase());
    if (!base.isAbsolute()) {
      base = new File(System.getProperty("catalina.base"), host.getAppBase());
    }
    return base;
  }

  /* (non-Javadoc)
   * @see com.googlecode.psiprobe.TomcatContainer#getConfigBase()
   */
  @Override
  public String getConfigBase() {
    return getConfigBase(host);
  }

  /* (non-Javadoc)
   * @see com.googlecode.psiprobe.TomcatContainer#getHostName()
   */
  @Override
  public String getHostName() {
    return host.getName();
  }

  /* (non-Javadoc)
   * @see com.googlecode.psiprobe.TomcatContainer#getName()
   */
  @Override
  public String getName() {
    return host.getParent().getName();
  }

  /**
   * Gets the filter mappings.
   *
   * @param fmap the fmap
   * @param dm the dm
   * @param filterClass the filter class
   * @return the filter mappings
   */
  protected List<FilterMapping> getFilterMappings(FilterMap fmap, String dm, String filterClass) {
    String[] urls = fmap.getURLPatterns();
    String[] servlets = fmap.getServletNames();
    List<FilterMapping> results = new ArrayList<FilterMapping>(urls.length + servlets.length);
    for (String url : urls) {
      FilterMapping fm = new FilterMapping();
      fm.setUrl(url);
      fm.setFilterName(fmap.getFilterName());
      fm.setDispatcherMap(dm);
      fm.setFilterClass(filterClass);
      results.add(fm);
    }
    for (String servlet : servlets) {
      FilterMapping fm = new FilterMapping();
      fm.setServletName(servlet);
      fm.setFilterName(fmap.getFilterName());
      fm.setDispatcherMap(dm);
      fm.setFilterClass(filterClass);
      results.add(fm);
    }
    return results;
  }

  /* (non-Javadoc)
   * @see com.googlecode.psiprobe.AbstractTomcatContainer#createJspCompilationContext(java.lang.String, org.apache.jasper.Options, javax.servlet.ServletContext, org.apache.jasper.compiler.JspRuntimeContext, java.lang.ClassLoader)
   */
  @Override
  protected JspCompilationContext createJspCompilationContext(String name, Options opt,
      ServletContext sctx, JspRuntimeContext jrctx, ClassLoader classLoader) {
    
    JspCompilationContext jcctx = new JspCompilationContext(name, opt, sctx, null, jrctx);
    jcctx.setClassLoader(classLoader);
    return jcctx;
  }

  /* (non-Javadoc)
   * @see com.googlecode.psiprobe.TomcatContainer#addContextResourceLink(org.apache.catalina.Context, java.util.List, boolean)
   */
  @Override
  public void addContextResourceLink(Context context, List<ApplicationResource> resourceList,
      boolean contextBound) {

    for (ContextResourceLink link : context.getNamingResources().findResourceLinks()) {
      ApplicationResource resource = new ApplicationResource();
      logger.debug("reading resourceLink: " + link.getName());
      resource.setApplicationName(context.getName());
      resource.setName(link.getName());
      resource.setType(link.getType());
      resource.setLinkTo(link.getGlobal());

      // lookupResource(resource, contextBound, false);
      resourceList.add(resource);
    }
  }

  /* (non-Javadoc)
   * @see com.googlecode.psiprobe.TomcatContainer#addContextResource(org.apache.catalina.Context, java.util.List, boolean)
   */
  @Override
  public void addContextResource(Context context, List<ApplicationResource> resourceList,
      boolean contextBound) {

    NamingResourcesImpl namingResources = context.getNamingResources();
    for (ContextResource contextResource : namingResources.findResources()) {
      ApplicationResource resource = new ApplicationResource();

      logger.info("reading resource: " + contextResource.getName());
      resource.setApplicationName(context.getName());
      resource.setName(contextResource.getName());
      resource.setType(contextResource.getType());
      resource.setScope(contextResource.getScope());
      resource.setAuth(contextResource.getAuth());
      resource.setDescription(contextResource.getDescription());

      // lookupResource(resource, contextBound, false);
      resourceList.add(resource);
    }
  }

  /* (non-Javadoc)
   * @see com.googlecode.psiprobe.TomcatContainer#getApplicationFilterMaps(org.apache.catalina.Context)
   */
  @Override
  public List<FilterMapping> getApplicationFilterMaps(Context context) {
    FilterMap[] fms = context.findFilterMaps();
    List<FilterMapping> filterMaps = new ArrayList<FilterMapping>(fms.length);
    for (FilterMap filterMap : fms) {
      if (filterMap != null) {
        String dm;
        switch (filterMap.getDispatcherMapping()) {
          case FilterMap.ERROR:
            dm = "ERROR";
            break;
          case FilterMap.FORWARD:
            dm = "FORWARD";
            break;
          // case FilterMap.FORWARD_ERROR: dm = "FORWARD,ERROR"; break;
          case FilterMap.INCLUDE:
            dm = "INCLUDE";
            break;
          // case FilterMap.INCLUDE_ERROR: dm = "INCLUDE,ERROR"; break;
          // case FilterMap.INCLUDE_ERROR_FORWARD: dm = "INCLUDE,ERROR,FORWARD"; break;
          // case FilterMap.INCLUDE_FORWARD: dm = "INCLUDE,FORWARD"; break;
          case FilterMap.REQUEST:
            dm = "REQUEST";
            break;
          // case FilterMap.REQUEST_ERROR: dm = "REQUEST,ERROR"; break;
          // case FilterMap.REQUEST_ERROR_FORWARD: dm = "REQUEST,ERROR,FORWARD"; break;
          // case FilterMap.REQUEST_ERROR_FORWARD_INCLUDE: dm = "REQUEST,ERROR,FORWARD,INCLUDE";
          // break;
          // case FilterMap.REQUEST_ERROR_INCLUDE: dm = "REQUEST,ERROR,INCLUDE"; break;
          // case FilterMap.REQUEST_FORWARD: dm = "REQUEST,FORWARD"; break;
          // case FilterMap.REQUEST_INCLUDE: dm = "REQUEST,INCLUDE"; break;
          // case FilterMap.REQUEST_FORWARD_INCLUDE: dm = "REQUEST,FORWARD,INCLUDE"; break;
          default:
            dm = "";
        }

        String filterClass = "";
        FilterDef fd = context.findFilterDef(filterMap.getFilterName());
        if (fd != null) {
          filterClass = fd.getFilterClass();
        }

        List<FilterMapping> filterMappings = getFilterMappings(filterMap, dm, filterClass);
        filterMaps.addAll(filterMappings);
      }
    }
    return filterMaps;
  }

  /* (non-Javadoc)
   * @see com.googlecode.psiprobe.TomcatContainer#getApplicationFilters(org.apache.catalina.Context)
   */
  @Override
  public List<FilterInfo> getApplicationFilters(Context context) {
    FilterDef[] fds = context.findFilterDefs();
    List<FilterInfo> filterDefs = new ArrayList<FilterInfo>(fds.length);
    for (FilterDef filterDef : fds) {
      if (filterDef != null) {
        FilterInfo fi = getFilterInfo(filterDef);
        filterDefs.add(fi);
      }
    }
    return filterDefs;
  }

  /**
   * Gets the filter info.
   *
   * @param fd the fd
   * @return the filter info
   */
  private static FilterInfo getFilterInfo(FilterDef fd) {
    FilterInfo fi = new FilterInfo();
    fi.setFilterName(fd.getFilterName());
    fi.setFilterClass(fd.getFilterClass());
    fi.setFilterDesc(fd.getDescription());
    return fi;
  }

  /* (non-Javadoc)
   * @see com.googlecode.psiprobe.TomcatContainer#getApplicationInitParams(org.apache.catalina.Context)
   */
  @Override
  public List<ApplicationParam> getApplicationInitParams(Context context) {
    /*
     * We'll try to determine if a parameter value comes from a deployment descriptor or a context
     * descriptor.
     * 
     * Assumption: context.findParameter() returns only values of parameters that are declared in a
     * deployment descriptor.
     * 
     * If a parameter is declared in a context descriptor with override=false and redeclared in a
     * deployment descriptor, context.findParameter() still returns its value, even though the value
     * is taken from a context descriptor.
     * 
     * context.findApplicationParameters() returns all parameters that are declared in a context
     * descriptor regardless of whether they are overridden in a deployment descriptor or not or
     * not.
     */

    /*
     * creating a set of parameter names that are declared in a context descriptor and can not be
     * ovevridden in a deployment descriptor.
     */
    Set<String> nonOverridableParams = new HashSet<String>();
    for (ApplicationParameter appParam : context.findApplicationParameters()) {
      if (appParam != null && !appParam.getOverride()) {
        nonOverridableParams.add(appParam.getName());
      }
    }

    List<ApplicationParam> initParams = new ArrayList<ApplicationParam>();
    ServletContext servletCtx = context.getServletContext();
    for (Enumeration e = servletCtx.getInitParameterNames(); e.hasMoreElements();) {
      String paramName = (String) e.nextElement();

      ApplicationParam param = new ApplicationParam();
      param.setName(paramName);
      param.setValue(servletCtx.getInitParameter(paramName));
      /*
       * if the parameter is declared in a deployment descriptor and it is not declared in a context
       * descriptor with override=false, the value comes from the deployment descriptor
       */
      param.setFromDeplDescr(context.findParameter(paramName) != null
          && !nonOverridableParams.contains(paramName));
      initParams.add(param);
    }

    return initParams;
  }

  /* (non-Javadoc)
   * @see com.googlecode.psiprobe.TomcatContainer#resourceExists(java.lang.String, org.apache.catalina.Context)
   */
  @Override
  public boolean resourceExists(String name, Context context) {
    return context.getResources().getResource(name) != null;
  }

  /* (non-Javadoc)
   * @see com.googlecode.psiprobe.TomcatContainer#getResourceStream(java.lang.String, org.apache.catalina.Context)
   */
  @Override
  public InputStream getResourceStream(String name, Context context) throws IOException {
    WebResource resource = context.getResources().getResource(name);
    return resource.getInputStream();
  }

  /* (non-Javadoc)
   * @see com.googlecode.psiprobe.TomcatContainer#getResourceAttributes(java.lang.String, org.apache.catalina.Context)
   */
  @Override
  public Long[] getResourceAttributes(String name, Context context) {
    Long[] result = new Long[2];
    WebResource resource = context.getResources().getResource(name);
    result[0] = resource.getContentLength();
    result[1] = resource.getLastModified();
    return result;
  }

  /**
   * Returns the security token required to bind to a naming context.
   *
   * @param context the catalina context
   *
   * @return the security token for use with <code>ContextBindings</code>
   */
  protected Object getNamingToken(Context context) {
    // null token worked before 8.0.6
    Object token = null;
    if (!ContextAccessController.checkSecurityToken(context, token)) {
      // namingToken added to Context and Server interfaces in 8.0.6
      // Used by NamingContextListener when settinp up JNDI context
      token = context.getNamingToken();
      if (!ContextAccessController.checkSecurityToken(context, token)) {
        logger.error("Couldn't get a valid security token. ClassLoader binding will fail.");
      }
    }

    return token;
  }

  /**
   * Binds a naming context to the current thread's classloader.
   * 
   * @param context the catalina context
   * @throws NamingException if binding the classloader fails
   */
  @Override
  public void bindToContext(Context context) throws NamingException {
    changeContextBinding(context, true);
  }

  /**
   * Unbinds a naming context from the current thread's classloader.
   * 
   * @param context the catalina context
   * @throws NamingException if unbinding the classloader fails
   */
  @Override
  public void unbindFromContext(Context context) throws NamingException {
    changeContextBinding(context, false);
  }
  
  /**
   * Change context binding.
   *
   * @param context the context
   * @param bind the bind
   * @throws NamingException the naming exception
   */
  private void changeContextBinding(Context context, boolean bind) throws NamingException {
    Object token = getNamingToken(context);
    ClassLoader loader = Thread.currentThread().getContextClassLoader();
    if (bind) {
      ContextBindings.bindClassLoader(context, token, loader);
    } else {
      ContextBindings.unbindClassLoader(context, token, loader);
    }
  }

}
