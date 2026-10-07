<%@ page contentType="text/html; charset=UTF-8" %>
<%@ include file="/jcore/doInitPage.jspf" %>
<%@ page import="co.kozao.jcmsplugin.squashtm.SquashTmManager" %>
<%
if (loggedMember == null) {
    sendForbidden(request, response);
    return;
}

if (SquashTmManager.getInstance().isConnect()) {
    sendRedirect(jcmsContext.getBaseUrl() + "plugins/SquashTmPlugin/jsp/app/squashTm.jsp");
    return;
}
%>
<%@ include file="/jcore/doHeader.jspf" %>

<div class="container-fluid">
    <div class="jumbotron">
        <h1><%= glp("jcmsplugin.squashtm.auth.callback.title") %></h1>
    </div>
    <jalios:message level="ERROR" msg="jcmsplugin.squashtm.auth.callback.error"/>
    <a class="btn btn-primary" href="plugins/SquashTmPlugin/jsp/app/squashTm.jsp">
        <%= glp("jcmsplugin.squashtm.auth.callback.back") %>
    </a>
</div>

<%@ include file="/jcore/doFooter.jspf" %>