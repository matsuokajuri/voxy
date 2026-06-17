package me.cortex.voxy.forge;

record ForgeFormalRendererBlocker(
        String severity,
        String id,
        String title,
        String reason,
        String nextAction,
        boolean formalDrawBlocking
) {
    String priority() {
        return this.severity;
    }

    String compact() {
        return this.severity + ":" + this.id;
    }

    String detailed() {
        return this.id
                + "{severity=" + this.severity
                + ",title=" + safe(this.title)
                + ",reason=" + safe(this.reason)
                + ",nextAction=" + safe(this.nextAction)
                + ",formalDrawBlocking=" + this.formalDrawBlocking
                + "}";
    }

    private static String safe(String value) {
        return value == null || value.isBlank() ? "unspecified" : value.replace(' ', '-');
    }
}
