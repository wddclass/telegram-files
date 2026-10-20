export const FORK_REPOSITORY = "wddclass/telegram-files";
export const FORK_IMAGE = "ghcr.io/wddclass/telegram-files";
export const TRACKED_BRANCH = "custom-0.3.1";
export const REPO_URL = `https://github.com/${FORK_REPOSITORY}`;
export const IMAGE_PACKAGE_URL = `https://github.com/${FORK_REPOSITORY}/pkgs/container/telegram-files`;
export const UPSTREAM_URL = "https://github.com/jarvis2f/telegram-files";

export interface RunningBuild {
  version?: string;
  commit?: string;
  commitFull?: string;
  ref?: string;
  image?: string;
  repository?: string;
  branch?: string;
}

export interface RemoteCommit {
  sha: string;
  htmlUrl: string;
  message: string;
}

export interface RemoteRelease {
  tag: string;
  htmlUrl: string;
}

export interface CompareResult {
  status: "identical" | "behind" | "ahead" | "diverged" | "unknown";
  /** Commits on the remote ref that this build does not contain. */
  remoteAhead: number;
  /** Commits in this build that the remote ref does not contain. */
  localAhead: number;
}

export function normalizeSha(value?: string | null): string {
  const sha = (value ?? "").trim().toLowerCase();
  if (!sha || sha === "unknown") return "";
  return sha;
}

export function shortSha(value?: string | null, length = 7): string {
  const sha = normalizeSha(value);
  if (!sha) return "";
  return sha.slice(0, Math.min(length, sha.length));
}

export function runningSha(build?: RunningBuild | null): string {
  return normalizeSha(build?.commitFull) || normalizeSha(build?.commit);
}

export function imageRef(tag: string): string {
  return `${FORK_IMAGE}:${tag}`;
}

export function shaImage(value?: string | null): string {
  const short = shortSha(value);
  return short ? imageRef(`sha-${short}`) : imageRef("custom-latest");
}

export function pickCustomRelease<T extends { tag_name?: string }>(
  releases: T[],
): T | undefined {
  return releases.find((release) => release.tag_name?.startsWith("custom-"));
}

export function parseCompare(payload: {
  status?: string;
  ahead_by?: number;
  behind_by?: number;
} | null): CompareResult {
  const status = payload?.status;
  if (
    status === "identical" ||
    status === "behind" ||
    status === "ahead" ||
    status === "diverged"
  ) {
    return {
      status,
      remoteAhead: payload?.ahead_by ?? 0,
      localAhead: payload?.behind_by ?? 0,
    };
  }
  return { status: "unknown", remoteAhead: 0, localAhead: 0 };
}

/**
 * GitHub compare `{current}...{remote}` reports `ahead_by` as commits the
 * remote ref has that the running revision does not.
 */
export function remoteHasUpdates(result: CompareResult | null | undefined): boolean {
  return !!result && result.remoteAhead > 0;
}
