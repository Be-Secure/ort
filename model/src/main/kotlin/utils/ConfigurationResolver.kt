/*
 * Copyright (C) 2023 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 * License-Filename: LICENSE
 */

package org.ossreviewtoolkit.model.utils

import kotlin.time.measureTimedValue

import org.apache.logging.log4j.kotlin.Logging
import org.apache.logging.log4j.kotlin.logger

import org.ossreviewtoolkit.model.AnalyzerResult
import org.ossreviewtoolkit.model.PackageCuration
import org.ossreviewtoolkit.model.PackageCurationsEntry
import org.ossreviewtoolkit.model.ResolvedConfiguration

object ConfigurationResolver : Logging {
    /**
     * Return the resolved configuration for the given [analyzerResult]. The [curationProviders] must be ordered
     * highest-priority-first.
     */
    fun resolveConfiguration(
        analyzerResult: AnalyzerResult,
        curationProviders: List<Pair<String, PackageCurationProvider>>
    ): ResolvedConfiguration {
        val packageCurations = mutableMapOf<String, Set<PackageCuration>>()

        curationProviders.forEach { (id, curationProvider) ->
            val (curations, duration) = measureTimedValue {
                curationProvider.getCurationsFor(analyzerResult.packages)
            }

            val (applicableCurations, nonApplicableCurations) = curations.partition { curation ->
                analyzerResult.packages.any { pkg -> curation.isApplicable(pkg.id) }
            }.let { it.first.toSet() to it.second.toSet() }

            if (nonApplicableCurations.isNotEmpty()) {
                logger.warn {
                    "The provider '$id' returned the following non-applicable curations: " +
                            "${nonApplicableCurations.joinToString()}."
                }
            }

            packageCurations[id] = applicableCurations

            logger().info { "Getting ${curations.size} package curation(s) from provider '$id' took $duration." }
        }

        return ResolvedConfiguration(
            packageCurations = packageCurations.map { (providerId, curations) ->
                PackageCurationsEntry(
                    provider = PackageCurationsEntry.Provider(providerId),
                    curations = curations
                )
            }
        )
    }
}
