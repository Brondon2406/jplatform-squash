<%@ include file="/jcore/doInitPage.jspf" %>
<%@ include file="/front/app/doAppCommon.jspf" %>

<% if (loggedMember == null) {
    sendForbidden(request, response);
}%>

<div class="squashtm-sidebar sidebar-tabbed is-closed sidebar-component sidebar sidebar-right"></div>
	<div class="ajax-refresh-div" data-jalios-ajax-refresh-url="plugins/SquashTmPlugin/jsp/app/squashTm.jsp">
    	<%@ include file="/plugins/SquashTmPlugin/jsp/app/doSquashTm.jspf" %>
	</div>
<%@ include file="/jcore/doFooter.jspf" %>
