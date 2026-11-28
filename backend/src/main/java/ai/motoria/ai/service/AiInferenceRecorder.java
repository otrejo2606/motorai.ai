package ai.motoria.ai.service;

import ai.motoria.ai.domain.AiInference;
import ai.motoria.ai.repository.AiInferenceRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * Persists inferences in their own transaction so a failed business step never loses the audit record.
 */
@ApplicationScoped
public class AiInferenceRecorder {

    @Inject
    AiInferenceRepository aiInferenceRepository;

    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public void save(AiInference inference) {
        aiInferenceRepository.persist(inference);
    }

    @Transactional
    public void recordHumanDecision(UUID inferenceId, String reviewer, String decision) {
        aiInferenceRepository.findByIdOptional(inferenceId)
                .ifPresent(inference -> inference.recordHumanDecision(reviewer, decision, Instant.now()));
    }
}
