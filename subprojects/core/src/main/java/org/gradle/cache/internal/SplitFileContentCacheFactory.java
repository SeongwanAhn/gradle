/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.cache.internal;

import org.gradle.api.internal.changedetection.state.FileCategorizer;
import org.gradle.internal.concurrent.CompositeStoppable;
import org.gradle.internal.serialize.Serializer;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;

/**
 * A {@link FileContentCacheFactory} that delegates to the global cache for files that are known to be immutable and shared between different builds.
 * All other requests are delegated to the build-local cache.
 */
public class SplitFileContentCacheFactory implements FileContentCacheFactory, Closeable {
    private final FileContentCacheFactory globalFactory;
    private final FileContentCacheFactory localFactory;
    private final FileCategorizer fileCategorizer;

    public SplitFileContentCacheFactory(FileContentCacheFactory globalFactory, FileContentCacheFactory localFactory, FileCategorizer fileCategorizer) {
        this.globalFactory = globalFactory;
        this.localFactory = localFactory;
        this.fileCategorizer = fileCategorizer;
    }

    @Override
    public void close() throws IOException {
        CompositeStoppable.stoppable(globalFactory, localFactory).stop();
    }

    @Override
    public <V> FileContentCache<V> newCache(String name, int normalizedCacheSize, Calculator<? extends V> calculator, Serializer<V> serializer) {
        FileContentCache<V> globalCache = globalFactory.newCache(name, normalizedCacheSize, calculator, serializer);
        FileContentCache<V> localCache = localFactory.newCache(name, normalizedCacheSize, calculator, serializer);
        return new SplitFileContentCache<V>(globalCache, localCache, fileCategorizer);
    }

    private static final class SplitFileContentCache<V> implements FileContentCache<V> {
        private final FileContentCache<V> globalCache;
        private final FileContentCache<V> localCache;
        private final FileCategorizer fileCategorizer;

        private SplitFileContentCache(FileContentCache<V> globalCache, FileContentCache<V> localCache, FileCategorizer fileCategorizer) {
            this.globalCache = globalCache;
            this.localCache = localCache;
            this.fileCategorizer = fileCategorizer;
        }

        @Override
        public V get(File file) {
            if (fileCategorizer.isImmutable(file.getPath())) {
                return globalCache.get(file);
            } else {
                return localCache.get(file);
            }
        }
    }
}
