import fs from 'fs-extra';
import path from 'path';
import { isError } from '../exceptions';

export const CLIMAT_HOME_DIR_NAME = 'climat';
export const MAIN_MANIFEST_NAME = 'climat.cli';

export async function moveManifestToClimatHome(
  toolchainHome: string,
  pathToManifest: string,
  name: string,
): Promise<void> {
  const path = platformPath();
  const toolchainDir = path.join(toolchainHome, name);
  await fs.ensureDir(toolchainHome);
  await fs.chmod(toolchainHome, 0o755);

  await fs.ensureDir(toolchainDir);
  await fs.chmod(toolchainDir, 0o755);

  await fs.writeFile(
    path.join(toolchainDir, MAIN_MANIFEST_NAME),
    pathToManifest,
  );
}

export async function removeToolchain(
  toolchainHome: string,
  name: string,
): Promise<void> {
  try {
    await fs.rm(platformPath().join(toolchainHome, name), {
      recursive: true,
    });
  } catch (e) {
    if (isError(e)) {
      if (e.code === 'ENOENT') {
        throw new Error(`Toolchain named \`${name}\` was not found`);
      }
    }
    throw e;
  }
}

export function platformPath(): path.PlatformPath {
  return handlePlatforms(
    () => path.posix,
    () => path.win32,
  );
}

export function handlePlatforms<T>(unix: () => T, windows: () => T): T {
  switch (process.platform) {
    case 'linux':
    case 'darwin':
      return unix();
    case 'win32':
      return windows();
    default:
      throw new Error(`${process.platform} OS is not supported`);
  }
}
