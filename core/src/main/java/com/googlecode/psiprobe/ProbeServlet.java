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

import com.googlecode.psiprobe.beans.ContainerWrapperBean;

import org.apache.catalina.ContainerServlet;
import org.apache.catalina.Wrapper;
import org.springframework.web.servlet.DispatcherServlet;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * Main dispatcher servlet. Spring default dispatcher servlet had to be superceeded to handle
 * "privileged" application context features. The actual requirement is to capture passed Wrapper
 * instance into ContainerWrapperBean. Wrapper instance is our gateway to Tomcat.
 * 
 * @author Vlad Ilyushchenko
 * @author Mark Lewis
 */
public class ProbeServlet extends DispatcherServlet implements ContainerServlet {

  /** The wrapper. */
  private Wrapper wrapper;

  /* (non-Javadoc)
   * @see org.apache.catalina.ContainerServlet#getWrapper()
   */
  public Wrapper getWrapper() {
    return wrapper;
  }

  /* (non-Javadoc)
   * @see org.apache.catalina.ContainerServlet#setWrapper(org.apache.catalina.Wrapper)
   */
  public void setWrapper(Wrapper wrapper) {
    this.wrapper = wrapper;
    logger.info("setWrapper() called");
  }

  /**
   * Associates the {@link Wrapper} with the {@link ContainerWrapperBean}.
   * 
   * @param config this servlet's configuration and initialization parameters
   * @throws ServletException if the wrapper is null or another servlet-interrupting error occurs
   */
  @Override
  public void init(ServletConfig config) throws ServletException {
    super.init(config);
    if (wrapper != null) {
      getContainerWrapperBean().setWrapper(wrapper);
    } else {
      throw new ServletException("Wrapper is null");
    }
  }

  /* (non-Javadoc)
   * @see org.springframework.web.servlet.DispatcherServlet#doDispatch(javax.servlet.http.HttpServletRequest, javax.servlet.http.HttpServletResponse)
   */
  @Override
  protected void doDispatch(HttpServletRequest httpServletRequest,
      HttpServletResponse httpServletResponse) throws Exception {

    httpServletRequest.setCharacterEncoding("UTF-8");
    super.doDispatch(httpServletRequest, httpServletResponse);
  }

  /* (non-Javadoc)
   * @see org.springframework.web.servlet.FrameworkServlet#destroy()
   */
  @Override
  public void destroy() {
    getContainerWrapperBean().setWrapper(null);
    super.destroy();
  }

  /**
   * Gets the container wrapper bean.
   *
   * @return the container wrapper bean
   */
  protected ContainerWrapperBean getContainerWrapperBean() {
    return (ContainerWrapperBean) getWebApplicationContext().getBean("containerWrapper");
  }

}
