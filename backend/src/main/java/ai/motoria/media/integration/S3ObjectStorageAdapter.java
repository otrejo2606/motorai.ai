package ai.motoria.media.integration;

import ai.motoria.common.exception.BusinessRuleException;
import ai.motoria.common.exception.DependencyUnavailableException;
import ai.motoria.media.exception.MediaErrorCodes;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.util.Optional;

/**
 * S3 adapter on the AWS SDK v2. Bucket names, region and optional endpoint (S3-compatible storage) come from
 * configuration; credentials come from the AWS default chain (environment or instance role), never from the repo.
 * Clients are created lazily so the application starts without storage configured (owner gate 0.2).
 */
@ApplicationScoped
public class S3ObjectStorageAdapter implements ObjectStoragePort {

    private final String region;
    private final Optional<String> bucket;
    private final Optional<String> publicBucket;
    private final Optional<String> endpoint;
    private final boolean pathStyle;
    private final Optional<String> publicBaseUrl;

    private S3Client client;
    private S3Presigner presigner;

    @Inject
    public S3ObjectStorageAdapter(
            @ConfigProperty(name = "motoria.media.s3.region", defaultValue = "us-east-1") String region,
            @ConfigProperty(name = "motoria.media.s3.bucket") Optional<String> bucket,
            @ConfigProperty(name = "motoria.media.s3.public-bucket") Optional<String> publicBucket,
            @ConfigProperty(name = "motoria.media.s3.endpoint") Optional<String> endpoint,
            @ConfigProperty(name = "motoria.media.s3.path-style", defaultValue = "false") boolean pathStyle,
            @ConfigProperty(name = "motoria.media.public-base-url") Optional<String> publicBaseUrl) {
        this.region = region;
        this.bucket = bucket;
        this.publicBucket = publicBucket;
        this.endpoint = endpoint;
        this.pathStyle = pathStyle;
        this.publicBaseUrl = publicBaseUrl;
    }

    @Override
    public PresignedUrl presignUpload(String key, String contentType, long contentLength, Duration ttl) {
        try {
            PutObjectRequest put = PutObjectRequest.builder()
                    .bucket(privateBucket())
                    .key(key)
                    .contentType(contentType)
                    .contentLength(contentLength)
                    .build();
            PresignedPutObjectRequest presigned = presigner().presignPutObject(PutObjectPresignRequest.builder()
                    .signatureDuration(ttl)
                    .putObjectRequest(put)
                    .build());
            return new PresignedUrl(presigned.url().toString(), presigned.expiration());
        } catch (SdkException exception) {
            throw unavailable(exception);
        }
    }

    @Override
    public PresignedUrl presignRead(String key, Duration ttl) {
        try {
            GetObjectRequest get = GetObjectRequest.builder()
                    .bucket(privateBucket())
                    .key(key)
                    .build();
            PresignedGetObjectRequest presigned = presigner().presignGetObject(GetObjectPresignRequest.builder()
                    .signatureDuration(ttl)
                    .getObjectRequest(get)
                    .build());
            return new PresignedUrl(presigned.url().toString(), presigned.expiration());
        } catch (SdkException exception) {
            throw unavailable(exception);
        }
    }

    @Override
    public Optional<Long> objectSize(String key) {
        try {
            Long size = client().headObject(HeadObjectRequest.builder()
                    .bucket(privateBucket())
                    .key(key)
                    .build()).contentLength();
            return Optional.ofNullable(size);
        } catch (NoSuchKeyException exception) {
            return Optional.empty();
        } catch (S3Exception exception) {
            if (exception.statusCode() == 404) {
                return Optional.empty();
            }
            throw unavailable(exception);
        } catch (SdkException exception) {
            throw unavailable(exception);
        }
    }

    @Override
    public byte[] read(String key, long maxBytes) {
        GetObjectRequest get = GetObjectRequest.builder()
                .bucket(privateBucket())
                .key(key)
                .build();
        try (ResponseInputStream<GetObjectResponse> input = client().getObject(get)) {
            int limit = (int) Math.min(Integer.MAX_VALUE - 8L, maxBytes + 1);
            byte[] content = input.readNBytes(limit);
            if (content.length > maxBytes) {
                throw new BusinessRuleException(MediaErrorCodes.MEDIA_TOO_LARGE, "The file is larger than the allowed size");
            }
            return content;
        } catch (IOException | SdkException exception) {
            throw unavailable(exception);
        }
    }

    @Override
    public void putPrivate(String key, byte[] content, String contentType) {
        put(privateBucket(), key, content, contentType);
    }

    @Override
    public void putPublic(String key, byte[] content, String contentType) {
        String target = publicBucket.filter(value -> !value.isBlank())
                .orElseThrow(() -> new DependencyUnavailableException(MediaErrorCodes.STORAGE_NOT_CONFIGURED,
                        "The public bucket is not configured"));
        put(target, key, content, contentType);
    }

    @Override
    public void delete(String key) {
        try {
            client().deleteObject(DeleteObjectRequest.builder()
                    .bucket(privateBucket())
                    .key(key)
                    .build());
        } catch (SdkException exception) {
            throw unavailable(exception);
        }
    }

    @Override
    public String publicUrl(String publicKey) {
        String base = publicBaseUrl.filter(value -> !value.isBlank())
                .orElseThrow(() -> new DependencyUnavailableException(MediaErrorCodes.STORAGE_NOT_CONFIGURED,
                        "The public base URL is not configured"));
        return base.endsWith("/") ? base + publicKey : base + "/" + publicKey;
    }

    @PreDestroy
    synchronized void close() {
        if (client != null) {
            client.close();
        }
        if (presigner != null) {
            presigner.close();
        }
    }

    private void put(String targetBucket, String key, byte[] content, String contentType) {
        try {
            client().putObject(PutObjectRequest.builder()
                            .bucket(targetBucket)
                            .key(key)
                            .contentType(contentType)
                            .contentLength((long) content.length)
                            .build(),
                    RequestBody.fromBytes(content));
        } catch (SdkException exception) {
            throw unavailable(exception);
        }
    }

    private String privateBucket() {
        return bucket.filter(value -> !value.isBlank())
                .orElseThrow(() -> new DependencyUnavailableException(MediaErrorCodes.STORAGE_NOT_CONFIGURED,
                        "The storage bucket is not configured"));
    }

    private synchronized S3Client client() {
        if (client == null) {
            S3ClientBuilder builder = S3Client.builder()
                    .region(Region.of(region))
                    .credentialsProvider(DefaultCredentialsProvider.create())
                    .httpClientBuilder(UrlConnectionHttpClient.builder())
                    .serviceConfiguration(serviceConfiguration());
            endpoint.filter(value -> !value.isBlank()).ifPresent(value -> builder.endpointOverride(URI.create(value)));
            client = builder.build();
        }
        return client;
    }

    private synchronized S3Presigner presigner() {
        if (presigner == null) {
            S3Presigner.Builder builder = S3Presigner.builder()
                    .region(Region.of(region))
                    .credentialsProvider(DefaultCredentialsProvider.create())
                    .serviceConfiguration(serviceConfiguration());
            endpoint.filter(value -> !value.isBlank()).ifPresent(value -> builder.endpointOverride(URI.create(value)));
            presigner = builder.build();
        }
        return presigner;
    }

    private S3Configuration serviceConfiguration() {
        return S3Configuration.builder()
                .pathStyleAccessEnabled(pathStyle)
                .build();
    }

    private DependencyUnavailableException unavailable(Exception exception) {
        return new DependencyUnavailableException(MediaErrorCodes.STORAGE_UNAVAILABLE, "The storage is not available", exception);
    }
}
