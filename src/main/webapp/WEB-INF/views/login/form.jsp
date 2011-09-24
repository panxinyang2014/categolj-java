<%@ page session="true"%>
<c:import url="/WEB-INF/views/layout/layout.jsp" charEncoding="UTF-8">
    <c:param name="title" value="login" />
    <c:param name="content">
        <form method="post" action="<c:url value="/j_spring_security_check" />">
            <c:if test="${not empty param.error}">
                <div class="alert-message error">
                    <a class="close" href="#" onclick="$(this).parent().hide()">x</a>
                    <p>
                        <strong>Login error!</strong> : ${sessionScope["SPRING_SECURITY_LAST_EXCEPTION"].message}
                    </p>
                </div>
            </c:if>
            <c:import url="/WEB-INF/views/login/fieldset.jsp" />
        </form>
    </c:param>
</c:import>
