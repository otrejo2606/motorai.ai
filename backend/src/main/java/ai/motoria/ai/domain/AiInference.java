package ai.motoria.ai.domain;

import ai.motoria.common.persistence.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Record of one model call (design D3, base requirement "Auditoria y trazabilidad de la IA"): model, prompt version,
 * input references, validated output, confidence, latency, cost and the human decision. The output never contains a
 * clear identity number.
 */
@Entity
@Table(name = "ai_inference", schema = "ai")
public class AiInference extends AuditableEntity {

    private static final int MAX_EXPLANATION = 2000;

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "use_case", nullable = false, length = 40)
    private AiUseCase useCase;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private AiInferenceStatus status;

    @Column(name = "provider", nullable = false, length = 60)
    private String provider;

    @Column(name = "model", nullable = false, length = 120)
    private String model;

    @Column(name = "prompt_version", nullable = false, length = 40)
    private String promptVersion;

    @Column(name = "subject_ref")
    private UUID subjectRef;

    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    @Column(name = "input_refs")
    private String inputRefs;

    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    @Column(name = "output_json")
    private String outputJson;

    @Column(name = "confidence", precision = 5, scale = 4)
    private BigDecimal confidence;

    @Column(name = "explanation", length = MAX_EXPLANATION)
    private String explanation;

    @Column(name = "error_code", length = 60)
    private String errorCode;

    @Column(name = "latency_ms")
    private Long latencyMs;

    @Column(name = "input_tokens")
    private Integer inputTokens;

    @Column(name = "output_tokens")
    private Integer outputTokens;

    @Column(name = "cost_usd", precision = 12, scale = 6)
    private BigDecimal costUsd;

    @Column(name = "reviewer", length = 255)
    private String reviewer;

    @Column(name = "human_decision", length = 60)
    private String humanDecision;

    @Column(name = "human_decision_at")
    private Instant humanDecisionAt;

    @Column(name = "correlation_id")
    private UUID correlationId;

    protected AiInference() {
    }

    public static AiInference start(AiUseCase useCase, String provider, String model, String promptVersion,
                                    UUID subjectRef, String inputRefs, UUID correlationId) {
        AiInference inference = new AiInference();
        inference.id = UUID.randomUUID();
        inference.useCase = Objects.requireNonNull(useCase);
        inference.provider = Objects.requireNonNull(provider);
        inference.model = Objects.requireNonNull(model);
        inference.promptVersion = Objects.requireNonNull(promptVersion);
        inference.subjectRef = subjectRef;
        inference.inputRefs = inputRefs;
        inference.correlationId = correlationId;
        inference.status = AiInferenceStatus.FAILED;
        return inference;
    }

    public void succeed(String outputJson, BigDecimal confidence, String explanation, Long latencyMs,
                        Integer inputTokens, Integer outputTokens, BigDecimal costUsd) {
        this.status = AiInferenceStatus.SUCCEEDED;
        this.outputJson = outputJson;
        this.confidence = confidence;
        this.explanation = truncate(explanation);
        this.latencyMs = latencyMs;
        this.inputTokens = inputTokens;
        this.outputTokens = outputTokens;
        this.costUsd = costUsd;
        this.errorCode = null;
    }

    public void fail(String errorCode, Long latencyMs) {
        this.status = AiInferenceStatus.FAILED;
        this.errorCode = errorCode;
        this.latencyMs = latencyMs;
    }

    public void recordHumanDecision(String reviewer, String decision, Instant decidedAt) {
        this.reviewer = reviewer;
        this.humanDecision = decision;
        this.humanDecisionAt = decidedAt;
    }

    private static String truncate(String value) {
        if (value == null) {
            return null;
        }
        return value.length() <= MAX_EXPLANATION ? value : value.substring(0, MAX_EXPLANATION);
    }

    public UUID getId() {
        return id;
    }

    public AiUseCase getUseCase() {
        return useCase;
    }

    public AiInferenceStatus getStatus() {
        return status;
    }

    public String getProvider() {
        return provider;
    }

    public String getModel() {
        return model;
    }

    public String getPromptVersion() {
        return promptVersion;
    }

    public UUID getSubjectRef() {
        return subjectRef;
    }

    public String getInputRefs() {
        return inputRefs;
    }

    public String getOutputJson() {
        return outputJson;
    }

    public BigDecimal getConfidence() {
        return confidence;
    }

    public String getExplanation() {
        return explanation;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public Long getLatencyMs() {
        return latencyMs;
    }

    public Integer getInputTokens() {
        return inputTokens;
    }

    public Integer getOutputTokens() {
        return outputTokens;
    }

    public BigDecimal getCostUsd() {
        return costUsd;
    }

    public String getReviewer() {
        return reviewer;
    }

    public String getHumanDecision() {
        return humanDecision;
    }

    public Instant getHumanDecisionAt() {
        return humanDecisionAt;
    }

    public UUID getCorrelationId() {
        return correlationId;
    }
}
