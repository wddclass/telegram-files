"use client";

import React from "react";
import useSWR from "swr";
import { Github, Package, RefreshCw } from "lucide-react";
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from "@/components/ui/card";
import Link from "next/link";
import TGDuck16HeyOut from "@/components/animations/tg-duck16_hey_out.json";
import dynamic from "next/dynamic";
import { useTranslations } from "next-intl";
import { Button } from "@/components/ui/button";
import {
  FORK_IMAGE,
  IMAGE_PACKAGE_URL,
  REPO_URL,
  TRACKED_BRANCH,
  UPSTREAM_URL,
  imageRef,
  parseCompare,
  pickCustomRelease,
  remoteHasUpdates,
  runningSha,
  shaImage,
  shortSha,
  type CompareResult,
  type RemoteCommit,
  type RemoteRelease,
  type RunningBuild,
} from "@/lib/update-check";

interface GithubCommitPayload {
  sha: string;
  html_url: string;
  commit?: { message?: string };
}

interface GithubReleasePayload {
  tag_name: string;
  html_url: string;
}

interface GithubComparePayload {
  status?: string;
  ahead_by?: number;
  behind_by?: number;
}

interface UpdateSnapshot {
  commit: RemoteCommit;
  release?: RemoteRelease;
  branchCompare: CompareResult | null;
  releaseCompare: CompareResult | null;
}

const Lottie = dynamic(() => import("lottie-react"), { ssr: false });

async function fetchJson<T>(url: string): Promise<T> {
  const response = await fetch(url, {
    headers: { Accept: "application/vnd.github+json" },
  });
  if (!response.ok) {
    throw new Error(`GitHub ${response.status}`);
  }
  return (await response.json()) as T;
}

async function loadUpdate(current: string): Promise<UpdateSnapshot> {
  const [commitPayload, releases] = await Promise.all([
    fetchJson<GithubCommitPayload>(
      `https://api.github.com/repos/${"wddclass/telegram-files"}/commits/${TRACKED_BRANCH}`,
    ),
    fetchJson<GithubReleasePayload[]>(
      `https://api.github.com/repos/wddclass/telegram-files/releases?per_page=20`,
    ),
  ]);
  const commit: RemoteCommit = {
    sha: commitPayload.sha,
    htmlUrl: commitPayload.html_url,
    message: commitPayload.commit?.message?.split("\n")[0] ?? "",
  };
  const picked = pickCustomRelease(releases);
  const release = picked
    ? { tag: picked.tag_name, htmlUrl: picked.html_url }
    : undefined;

  let branchCompare: CompareResult | null = null;
  let releaseCompare: CompareResult | null = null;
  if (current) {
    const branchUrl = `https://api.github.com/repos/wddclass/telegram-files/compare/${current}...${TRACKED_BRANCH}`;
    try {
      branchCompare = parseCompare(await fetchJson<GithubComparePayload>(branchUrl));
    } catch {
      branchCompare = { status: "unknown", remoteAhead: 0, localAhead: 0 };
    }
    if (release) {
      try {
        releaseCompare = parseCompare(
          await fetchJson<GithubComparePayload>(
            `https://api.github.com/repos/wddclass/telegram-files/compare/${current}...${encodeURIComponent(release.tag)}`,
          ),
        );
      } catch {
        releaseCompare = { status: "unknown", remoteAhead: 0, localAhead: 0 };
      }
    }
  }

  return { commit, release, branchCompare, releaseCompare };
}

function VersionRow({
  label,
  children,
}: {
  label: string;
  children: React.ReactNode;
}) {
  return (
    <div className="flex flex-col items-center justify-center gap-1">
      <p className="text-sm font-medium text-white">{label}</p>
      <div className="max-w-full text-center">{children}</div>
    </div>
  );
}

function ValuePill({ children }: { children: React.ReactNode }) {
  return (
    <p className="break-all rounded bg-gray-100 px-3 py-0.5 text-sm dark:bg-gray-800">
      {children}
    </p>
  );
}

export default function About() {
  const t = useTranslations("about");
  const { data: build, error: buildError } = useSWR<RunningBuild, Error>(
    "/version",
  );
  const current = runningSha(build);
  const {
    data: update,
    error: updateError,
    isLoading: updateLoading,
    mutate,
  } = useSWR<UpdateSnapshot, Error>(
    build ? ["fork-update", current] : null,
    () => loadUpdate(current),
  );

  const branchUpdate = remoteHasUpdates(update?.branchCompare);
  const releaseUpdate = remoteHasUpdates(update?.releaseCompare);
  const suggestedImage = branchUpdate
    ? imageRef("custom-latest")
    : releaseUpdate && update?.release
      ? imageRef(update.release.tag)
      : shaImage(current || update?.commit.sha);

  return (
    <div className="flex justify-center md:h-full md:items-center">
      <Card className="w-full bg-[radial-gradient(ellipse_at_bottom_left,_var(--tw-gradient-stops))] from-[#7900ff] via-[#548cff] to-[#93ffd8] md:w-1/2">
        <CardHeader>
          <CardTitle className="text-white">{t("title")}</CardTitle>
          <CardDescription className="text-white">
            {t("description")}
          </CardDescription>
        </CardHeader>
        <CardContent className="relative">
          <Lottie
            className="absolute bottom-3 right-3 h-28 w-28"
            animationData={TGDuck16HeyOut}
            loop={true}
          />
          <div className="space-y-4 pb-16">
            <VersionRow label={t("author")}>
              <p>Jarvis2f</p>
            </VersionRow>

            <VersionRow label={t("currentVersion")}>
              {buildError ? (
                <p className="text-red-200">{t("loadVersionFailed")}</p>
              ) : !build ? (
                <Loading />
              ) : (
                <ValuePill>{build.version || t("unknownRevision")}</ValuePill>
              )}
            </VersionRow>

            <VersionRow label={t("currentRevision")}>
              {build ? (
                <ValuePill>
                  {shortSha(current) || t("unknownRevision")}
                  {build.ref ? ` · ${build.ref}` : ""}
                </ValuePill>
              ) : buildError ? (
                <p className="text-red-200">{t("loadVersionFailed")}</p>
              ) : (
                <Loading />
              )}
            </VersionRow>

            <VersionRow label={t("latestBranch")}>
              {updateError ? (
                <p className="text-red-200">{t("loadUpdateFailed")}</p>
              ) : !update ? (
                updateLoading || !build ? (
                  <Loading />
                ) : (
                  <p className="text-red-200">{t("loadUpdateFailed")}</p>
                )
              ) : (
                <div className="space-y-1">
                  <ValuePill>
                    {shortSha(update.commit.sha)}
                    {update.commit.message ? ` · ${update.commit.message}` : ""}
                  </ValuePill>
                  <Link
                    href={update.commit.htmlUrl}
                    target="_blank"
                    rel="noopener noreferrer"
                    className="text-xs text-white underline"
                  >
                    {t("viewCommit")}
                  </Link>
                </div>
              )}
            </VersionRow>

            <VersionRow label={t("latestRelease")}>
              {update ? (
                update.release ? (
                  <div className="space-y-1">
                    <ValuePill>{update.release.tag}</ValuePill>
                    <Link
                      href={update.release.htmlUrl}
                      target="_blank"
                      rel="noopener noreferrer"
                      className="text-xs text-white underline"
                    >
                      {t("viewRelease")}
                    </Link>
                  </div>
                ) : (
                  <ValuePill>{t("noRelease")}</ValuePill>
                )
              ) : updateError ? (
                <p className="text-red-200">{t("loadUpdateFailed")}</p>
              ) : (
                <Loading />
              )}
            </VersionRow>

            <VersionRow label={t("image")}>
              <ValuePill>{build?.image || FORK_IMAGE}</ValuePill>
              <p className="mt-1 break-all text-xs text-white/90">
                {suggestedImage}
              </p>
            </VersionRow>

            {build && update && (
              <UpdateNotice
                current={current}
                branchUpdate={branchUpdate}
                releaseUpdate={releaseUpdate}
                update={update}
                suggestedImage={suggestedImage}
              />
            )}

            <p className="text-center text-xs text-white/90">{t("checkHint")}</p>
            <p className="text-center text-xs text-white/80">{t("tagsHint")}</p>

            <div className="flex items-center justify-center gap-4">
              <Link
                href={REPO_URL}
                target="_blank"
                rel="noopener noreferrer"
                className="inline-flex items-center gap-1 text-sm text-white"
                aria-label={t("repoLink")}
              >
                <Github className="h-5 w-5" />
                {t("repoLink")}
              </Link>
              <Link
                href={IMAGE_PACKAGE_URL}
                target="_blank"
                rel="noopener noreferrer"
                className="inline-flex items-center gap-1 text-sm text-white"
                aria-label={t("imageLink")}
              >
                <Package className="h-5 w-5" />
                {t("imageLink")}
              </Link>
              <Button
                type="button"
                size="sm"
                variant="secondary"
                onClick={() => void mutate()}
              >
                <RefreshCw className="mr-1 h-3 w-3" />
                {t("refresh")}
              </Button>
            </div>

            <p className="text-center text-xs text-white/80">
              {t("credit")}{" "}
              <Link
                href={UPSTREAM_URL}
                target="_blank"
                rel="noopener noreferrer"
                className="underline"
              >
                {t("upstream")}
              </Link>
            </p>
          </div>
        </CardContent>
      </Card>
    </div>
  );
}

function UpdateNotice({
  current,
  branchUpdate,
  releaseUpdate,
  update,
  suggestedImage,
}: {
  current: string;
  branchUpdate: boolean;
  releaseUpdate: boolean;
  update: UpdateSnapshot;
  suggestedImage: string;
}) {
  const t = useTranslations("about");
  if (!current) {
    return <Notice>{t("unknownCompare")}</Notice>;
  }
  if (!branchUpdate && !releaseUpdate) {
    return (
      <Notice>
        {t("upToDate")}
        {update.release && update.releaseCompare && !releaseUpdate
          ? ` ${t("releaseNotNewer")}`
          : ""}
      </Notice>
    );
  }
  return (
    <div className="space-y-2">
      {branchUpdate && (
        <Notice>
          {update.branchCompare?.status === "diverged"
            ? t("diverged", {
                count: update.branchCompare.remoteAhead,
              })
            : t("branchUpdate", {
                count: update.branchCompare?.remoteAhead ?? 0,
                image: suggestedImage,
              })}
        </Notice>
      )}
      {releaseUpdate && update.release && (
        <Notice>
          {t("releaseUpdate", {
            tag: update.release.tag,
          })}
        </Notice>
      )}
    </div>
  );
}

function Notice({ children }: { children: React.ReactNode }) {
  return (
    <div className="border-l-4 border-gray-700 bg-white px-4 py-2">
      <p className="text-sm text-gray-800">{children}</p>
    </div>
  );
}

function Loading() {
  const t = useTranslations("common");
  return (
    <div className="flex items-center space-x-2 text-white">
      <RefreshCw className="animate-spin" size={16} />
      <span>{t("loading")}</span>
    </div>
  );
}
