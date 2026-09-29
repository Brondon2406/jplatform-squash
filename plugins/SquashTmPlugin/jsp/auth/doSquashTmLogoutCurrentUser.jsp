<%@ page contentType="text/html; charset=UTF-8" %><%--
--%><%@ include file='/jcore/doInitPage.jspf' %>
<jsp:useBean id='formHandler' scope='page' class='co.kozao.jcmsplugin.squashtm.handler.SquashTmLogoutFormHandler'>
  <jsp:setProperty name='formHandler' property='request' value='<%= request %>'/>
  <jsp:setProperty name='formHandler' property='response' value='<%= response %>'/>
  <jsp:setProperty name='formHandler' property='*' />
</jsp:useBean>

<% if (formHandler.validate()) { 
  request.setAttribute("modal.redirect", formHandler.getRedirect()); 
%>
<%@ include file="/jcore/modal/modalRedirect.jspf" %>
<% return ; } 
%>
<jalios:modal formHandler="<%=formHandler %>" url="plugins/SquashTmPlugin/jsp/auth/doSquashTmLogoutCurrentUser.jsp" title="jcmsplugin.squashtm.authentication.auth.logout.modal.title" css="customCss" picture="images/jalios/icons/merge.png">
		<p><%= glp("jcmsplugin.squashtm.authentication.auth.logout.prevent-user.label") %></p>	
		 	
	 <jalios:buffer name='MODAL_BUTTONS'>
	    <button type="button" class="btn btn-default" onclick="jQuery.jalios.ui.Modal.close(false);"><%= glp("ui.com.btn.close") %></button>
	    <button style="background-color: #D9534F;" type="submit" class="btn btn-primary ajax-refresh" name = "submit" value="true"><%= glp("jcmsplugin.squashtm.authentication.auth.logout.modal.btn.title") %></button>
  	 </jalios:buffer>
</jalios:modal>