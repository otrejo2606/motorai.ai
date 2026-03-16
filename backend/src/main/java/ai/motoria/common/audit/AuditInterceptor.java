package ai.motoria.common.audit;

import io.quarkus.logging.Log;
import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.interceptor.AroundInvoke;
import jakarta.interceptor.Interceptor;
import jakarta.interceptor.InvocationContext;
import org.eclipse.microprofile.jwt.JsonWebToken;

@Audited
@Interceptor
@Priority(Interceptor.Priority.APPLICATION)
public class AuditInterceptor {

    @Inject
    JsonWebToken jwt;

    @AroundInvoke
    Object logAudit(InvocationContext context) throws Exception {
        String user = (jwt != null && jwt.getSubject() != null) ? jwt.getSubject() : "anonymous";
        String action = context.getMethod().getDeclaringClass().getSimpleName() + "." + context.getMethod().getName();
        try {
            Object result = context.proceed();
            Log.infov("AUDIT OK | user={0} | action={1}", user, action);
            return result;
        } catch (Exception exception) {
            Log.warnv("AUDIT FAIL | user={0} | action={1} | error={2}", user, action, exception.getMessage());
            throw exception;
        }
    }
}