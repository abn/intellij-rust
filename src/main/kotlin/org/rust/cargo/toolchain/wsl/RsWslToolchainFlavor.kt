/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.toolchain.wsl

import com.intellij.execution.wsl.WSLDistribution
import com.intellij.execution.wsl.WSLUtil
import com.intellij.execution.wsl.WslDistributionManager
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapiext.isDispatchThread
import com.intellij.util.io.isDirectory
import org.rust.cargo.toolchain.flavors.RsToolchainFlavor
import org.rust.ide.experiments.RsExperiments
import org.rust.openapiext.computeWithCancelableProgress
import org.rust.openapiext.isFeatureEnabled
import org.rust.stdext.toPath
import java.nio.file.Path

class RsWslToolchainFlavor : RsToolchainFlavor() {

    @Suppress("DEPRECATION", "UnstableApiUsage")
    override fun getHomePathCandidates(): List<Path> {
        val cargoBinPaths = mutableListOf<Path>()
        val sysPaths = mutableListOf<Path>()
        val unixPaths = mutableListOf<Path>()

        val runnable = {
            val distributions = WslDistributionManager.getInstance().installedDistributions
            val environments = distributions.map { it.environment }
            distributions to environments
        }

        val (distributions, environments) = if (isDispatchThread) {
            val project = ProjectManager.getInstance().defaultProject
            project.computeWithCancelableProgress("Getting installed distributions and environments...", runnable)
        } else {
            runnable()
        }

        for ((distro, environment) in distributions.zip(environments)) {
            // BACKCOMPAT: 2020.3
            // Replace with `distro.uncRootPath`
            val root = distro.uncRoot.path.toPath()

            for (remotePath in listOf("/usr/local/bin", "/usr/bin")) {
                val localPath = root.resolve(remotePath)
                if (!localPath.isDirectory()) continue
                unixPaths.add(localPath)
            }

            val sysPath = environment["PATH"].orEmpty()
            for (remotePath in sysPath.split(":")) {
                if (remotePath.isEmpty()) continue
                val localPath = root.resolve(remotePath)
                if (!localPath.isDirectory()) continue
                sysPaths.add(localPath)
            }

            val home = environment["HOME"] ?: continue
            val remoteCargoPath = "$home/.cargo/bin"
            val localCargoPath = root.resolve(remoteCargoPath)
            if (localCargoPath.isDirectory()) {
                cargoBinPaths.add(localCargoPath)
            }
        }

        return cargoBinPaths + sysPaths + unixPaths
    }

    override fun isApplicable(): Boolean =
        WSLUtil.isSystemCompatible() && isFeatureEnabled(RsExperiments.WSL_TOOLCHAIN_FLAVOR)

    // BACKCOMPAT: 2020.3
    // Replace with [WslDistributionManager.isWslPath]
    override fun isValidToolchainPath(path: Path): Boolean =
        path.toString().startsWith(WSLDistribution.UNC_PREFIX) && super.isValidToolchainPath(path)

    override fun hasExecutable(path: Path, toolName: String): Boolean = path.hasExecutableOnWsl(toolName)

    override fun pathToExecutable(path: Path, toolName: String): Path = path.pathToExecutableOnWsl(toolName)
}
