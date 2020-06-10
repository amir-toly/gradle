/*
 * Copyright 2012 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.api.publish.maven.tasks;

import org.gradle.api.InvalidUserDataException;
import org.gradle.api.artifacts.repositories.MavenArtifactRepository;
import org.gradle.api.internal.artifacts.BaseRepositoryFactory;
import org.gradle.api.publish.internal.PublishOperation;
import org.gradle.api.publish.maven.internal.publication.MavenPublicationInternal;
import org.gradle.api.publish.maven.internal.publisher.MavenNormalizedPublication;
import org.gradle.api.publish.maven.internal.publisher.MavenPublisher;
import org.gradle.api.publish.maven.internal.publisher.ValidatingMavenPublisher;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.TaskAction;
import org.gradle.internal.Cached;
import org.gradle.internal.service.ServiceRegistry;

import java.net.URI;

/**
 * Publishes a {@link org.gradle.api.publish.maven.MavenPublication} to a {@link MavenArtifactRepository}.
 *
 * @since 1.4
 */
public class PublishToMavenRepository extends AbstractPublishToMaven {
    private transient MavenArtifactRepository repository;
    private final Cached<PublishSpec> spec = Cached.of(this::computeSpec);

    /**
     * The repository to publish to.
     *
     * @return The repository to publish to
     */
    @Internal
    public MavenArtifactRepository getRepository() {
        return repository;
    }

    /**
     * Sets the repository to publish to.
     *
     * @param repository The repository to publish to
     */
    public void setRepository(MavenArtifactRepository repository) {
        this.repository = repository;
    }

    @TaskAction
    public void publish() {
        PublishSpec spec = this.spec.get();
        doPublish(spec.publication, spec.repository.get(getServices()));
    }

    static abstract class RepositorySpec {

        static RepositorySpec of(MavenArtifactRepository repository) {
            return new Configured(repository);
        }

        abstract MavenArtifactRepository get(ServiceRegistry services);

        static class Configured extends RepositorySpec implements java.io.Serializable {
            final MavenArtifactRepository repository;

            public Configured(MavenArtifactRepository repository) {
                this.repository = repository;
            }

            @Override
            MavenArtifactRepository get(ServiceRegistry services) {
                return repository;
            }

            private Object writeReplace() {
                if ("file".equals(repository.getUrl().getScheme())) {
                    return new LocalRepositorySpec(repository.getUrl());
                }
                return this;
            }
        }

        static class LocalRepositorySpec extends RepositorySpec {

            private final URI repositoryUrl;

            public LocalRepositorySpec(URI repositoryUrl) {
                this.repositoryUrl = repositoryUrl;
            }

            @Override
            MavenArtifactRepository get(ServiceRegistry services) {
                MavenArtifactRepository repository = services.get(BaseRepositoryFactory.class).createMavenRepository();
                repository.setUrl(repositoryUrl);
                return repository;
            }
        }
    }

    static class PublishSpec {

        private final RepositorySpec repository;
        private final MavenNormalizedPublication publication;

        public PublishSpec(
            RepositorySpec repository,
            MavenNormalizedPublication publication
        ) {
            this.repository = repository;
            this.publication = publication;
        }
    }

    private PublishSpec computeSpec() {
        MavenPublicationInternal publicationInternal = getPublicationInternal();
        if (publicationInternal == null) {
            throw new InvalidUserDataException("The 'publication' property is required");
        }

        MavenArtifactRepository repository = getRepository();
        if (repository == null) {
            throw new InvalidUserDataException("The 'repository' property is required");
        }

        getDuplicatePublicationTracker().checkCanPublish(publicationInternal, repository.getUrl(), repository.getName());
        MavenNormalizedPublication normalizedPublication = publicationInternal.asNormalisedPublication();
        return new PublishSpec(
            RepositorySpec.of(repository),
            normalizedPublication
        );
    }

    private void doPublish(final MavenNormalizedPublication normalizedPublication, final MavenArtifactRepository repository) {
        new PublishOperation(normalizedPublication.getName(), repository.getName()) {
            @Override
            protected void publish() {
                validatingMavenPublisher().publish(normalizedPublication, repository);
            }
        }.run();
    }

    private MavenPublisher validatingMavenPublisher() {
        return new ValidatingMavenPublisher(
            getMavenPublishers().getRemotePublisher(getTemporaryDirFactory())
        );
    }
}
