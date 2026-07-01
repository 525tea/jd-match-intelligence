#!/usr/bin/env python3
"""Evaluate JobFlow search quality with NDCG and TF-IDF skill reranking."""

from __future__ import annotations

import argparse
import csv
import json
import math
import os
import re
import sys
import urllib.error
import urllib.parse
import urllib.request
from dataclasses import dataclass
from pathlib import Path
from typing import Any


@dataclass(frozen=True)
class QueryCase:
    category: str
    keyword: str
    expected_roles: tuple[str, ...]
    strong_terms: tuple[str, ...]
    support_terms: tuple[str, ...]
    skill_terms: tuple[str, ...]
    critical_terms: tuple[str, ...] = ()


QUERY_CASES: tuple[QueryCase, ...] = (
    QueryCase(
        "synonym",
        "k8s 인프라",
        ("DEVOPS", "SRE", "SYSTEM_NETWORK"),
        ("kubernetes", "k8s", "쿠버네티스", "인프라"),
        ("platform", "플랫폼", "devops", "sre", "cloud", "클라우드"),
        ("kubernetes", "k8s", "docker", "aws", "terraform", "helm"),
    ),
    QueryCase(
        "synonym",
        "js 프론트엔드",
        ("FRONTEND", "FULLSTACK"),
        ("javascript", "자바스크립트", "프론트엔드", "frontend"),
        ("react", "vue", "next", "ui", "web"),
        ("javascript", "react", "vue", "next", "typescript"),
    ),
    QueryCase(
        "synonym",
        "ts 프론트엔드",
        ("FRONTEND", "FULLSTACK"),
        ("typescript", "타입스크립트", "프론트엔드", "frontend"),
        ("react", "vue", "next", "ui", "web"),
        ("typescript", "react", "vue", "next", "javascript"),
    ),
    QueryCase(
        "synonym",
        "py 데이터",
        ("DATA_ENGINEER", "DATA_SCIENTIST", "DATA_ANALYST", "ML_ENGINEER", "AI_ENGINEER"),
        ("python", "파이썬", "데이터"),
        ("data", "analytics", "분석", "머신러닝", "ml"),
        ("python", "sql", "spark", "airflow", "pandas"),
    ),
    QueryCase(
        "synonym",
        "mlops 엔지니어",
        ("ML_ENGINEER", "AI_ENGINEER", "MLOPS", "DEVOPS", "SRE"),
        ("mlops", "machine learning", "머신러닝"),
        ("ai", "ml", "model", "모델", "platform", "플랫폼"),
        ("mlops", "python", "kubernetes", "docker", "pytorch", "tensorflow"),
    ),
    QueryCase(
        "synonym",
        "llm 엔지니어",
        ("LLM", "GENERATIVE_AI", "AI_ENGINEER", "ML_ENGINEER", "AI_RESEARCHER"),
        ("llm", "생성형", "ai", "인공지능"),
        ("rag", "chatgpt", "gpt", "머신러닝", "딥러닝"),
        ("llm", "rag", "python", "pytorch", "tensorflow", "vector"),
    ),
    QueryCase(
        "special_token",
        "C++ 개발자",
        ("SOFTWARE_ENGINEER", "EMBEDDED_SOFTWARE", "ROBOT_SOFTWARE", "GAME_CLIENT", "HARDWARE_ENGINEER"),
        ("c++", "cplusplus"),
        ("임베디드", "로봇", "게임", "소프트웨어"),
        ("c++", "cplusplus", "linux", "embedded", "unreal", "robot"),
    ),
    QueryCase(
        "special_token",
        "Node.js 백엔드",
        ("BACKEND", "FULLSTACK"),
        ("node", "node.js", "nodejs", "백엔드", "backend"),
        ("api", "server", "서버", "typescript"),
        ("node", "node.js", "nodejs", "typescript", "express", "nestjs"),
    ),
    QueryCase(
        "special_token",
        ".NET 개발자",
        ("BACKEND", "FULLSTACK", "SOFTWARE_ENGINEER"),
        (".net", "dotnet", "c#"),
        ("백엔드", "backend", "software", "소프트웨어"),
        (".net", "dotnet", "c#", "asp.net", "mssql"),
    ),
    QueryCase(
        "special_token",
        "C# 개발자",
        ("BACKEND", "FULLSTACK", "SOFTWARE_ENGINEER", "GAME_CLIENT"),
        ("c#", ".net", "dotnet"),
        ("백엔드", "backend", "unity", "game", "게임"),
        ("c#", ".net", "dotnet", "unity", "mssql"),
    ),
    QueryCase(
        "special_token",
        "Vue.js 프론트엔드",
        ("FRONTEND", "FULLSTACK"),
        ("vue", "vue.js", "vuejs", "프론트엔드", "frontend"),
        ("javascript", "typescript", "ui", "web"),
        ("vue", "vue.js", "vuejs", "javascript", "typescript", "pinia"),
    ),
    QueryCase(
        "korean_role",
        "백엔드 개발자",
        ("BACKEND",),
        ("백엔드", "backend"),
        ("java", "spring", "api", "server", "서버"),
        ("java", "spring", "spring boot", "jpa", "mysql", "redis", "kafka"),
    ),
    QueryCase(
        "korean_role",
        "프론트엔드 개발자",
        ("FRONTEND",),
        ("프론트엔드", "frontend"),
        ("react", "vue", "next", "typescript", "javascript", "ui"),
        ("react", "vue", "next", "typescript", "javascript"),
    ),
    QueryCase(
        "korean_role",
        "데이터 엔지니어",
        ("DATA_ENGINEER", "DATA_SCIENTIST", "DATA_ANALYST", "DEVOPS"),
        ("데이터 엔지니어", "data engineer", "데이터 플랫폼"),
        ("데이터과학", "데이터 사이언티스트", "데이터 분석가", "etl"),
        ("kafka", "spark", "airflow", "etl", "python", "sql"),
    ),
    QueryCase(
        "korean_role",
        "보안 엔지니어",
        ("SECURITY", "SYSTEM_NETWORK"),
        ("보안", "security"),
        ("network", "네트워크", "취약점", "관제"),
        ("security", "network", "siem", "vulnerability", "firewall", "linux"),
    ),
    QueryCase(
        "korean_role",
        "안드로이드 개발자",
        ("ANDROID", "MOBILE", "APP"),
        ("안드로이드", "android"),
        ("모바일", "mobile", "app", "앱", "kotlin"),
        ("android", "kotlin", "java", "mobile", "app"),
    ),
    QueryCase(
        "composite",
        "Java Spring 백엔드",
        ("BACKEND",),
        ("java", "spring", "백엔드", "backend"),
        ("api", "server", "서버", "jpa"),
        ("java", "spring", "spring boot", "jpa", "mysql", "redis"),
    ),
    QueryCase(
        "composite",
        "React TypeScript 프론트엔드",
        ("FRONTEND", "FULLSTACK"),
        ("react", "typescript", "프론트엔드", "frontend"),
        ("next", "javascript", "ui", "web"),
        ("react", "typescript", "next", "javascript", "redux"),
    ),
    QueryCase(
        "composite",
        "Python 데이터 엔지니어",
        ("DATA_ENGINEER", "DATA_SCIENTIST", "DATA_ANALYST"),
        ("python", "데이터 엔지니어", "data engineer"),
        ("spark", "airflow", "etl", "sql", "analytics"),
        ("python", "sql", "spark", "airflow", "etl", "kafka"),
    ),
    QueryCase(
        "composite",
        "AWS DevOps",
        ("DEVOPS", "SRE", "SYSTEM_NETWORK"),
        ("aws", "devops", "데브옵스"),
        ("cloud", "클라우드", "인프라", "platform", "플랫폼"),
        ("aws", "kubernetes", "docker", "terraform", "helm"),
    ),
    QueryCase(
        "composite",
        "Kubernetes SRE",
        ("SRE", "DEVOPS", "SYSTEM_NETWORK"),
        ("kubernetes", "k8s", "sre"),
        ("platform", "플랫폼", "인프라", "cloud", "클라우드"),
        ("kubernetes", "k8s", "docker", "terraform", "prometheus"),
    ),
    QueryCase(
        "composite",
        "LLM Python 엔지니어",
        ("LLM", "GENERATIVE_AI", "AI_ENGINEER", "ML_ENGINEER"),
        ("llm", "python", "생성형", "ai"),
        ("rag", "gpt", "머신러닝", "딥러닝"),
        ("llm", "python", "rag", "pytorch", "tensorflow", "vector"),
    ),
    QueryCase(
        "composite",
        "Spring Boot JPA",
        ("BACKEND",),
        ("spring boot", "spring", "jpa"),
        ("java", "백엔드", "backend", "api", "server"),
        ("spring boot", "spring", "jpa", "java", "mysql"),
    ),
    QueryCase(
        "framework_combo",
        "Python Django",
        ("BACKEND", "FULLSTACK"),
        ("python", "django", "백엔드", "backend"),
        ("api", "server", "서버", "fastapi", "flask"),
        ("python", "django", "drf", "fastapi", "flask"),
        ("django",),
    ),
    QueryCase(
        "framework_combo",
        "Go Fiber",
        ("BACKEND", "FULLSTACK"),
        ("go", "golang", "fiber", "백엔드", "backend"),
        ("api", "server", "서버", "gin", "grpc"),
        ("go", "golang", "fiber", "gin", "grpc"),
        ("fiber",),
    ),
    QueryCase(
        "composite",
        "Kafka 데이터 플랫폼",
        ("DATA_ENGINEER", "DEVOPS", "SRE"),
        ("kafka", "데이터 플랫폼", "data platform"),
        ("stream", "스트리밍", "etl", "spark", "airflow"),
        ("kafka", "spark", "airflow", "python", "sql"),
    ),
    QueryCase(
        "edge_case",
        "backend junior seoul",
        ("BACKEND",),
        ("백엔드", "backend"),
        ("java", "spring", "seoul", "서울", "junior", "주니어"),
        ("java", "spring", "spring boot", "jpa", "mysql", "redis"),
    ),
    QueryCase(
        "edge_case",
        "remote backend",
        ("BACKEND",),
        ("backend", "백엔드", "remote", "원격", "재택"),
        ("server", "서버", "api", "java", "spring"),
        ("java", "spring", "api", "mysql", "redis"),
    ),
    QueryCase(
        "edge_case",
        "node 백엔드",
        ("BACKEND", "FULLSTACK"),
        ("node", "node.js", "nodejs", "백엔드", "backend"),
        ("typescript", "api", "server", "서버"),
        ("node", "node.js", "nodejs", "typescript", "nestjs", "express"),
    ),
    QueryCase(
        "edge_case",
        "리액트 frontend",
        ("FRONTEND", "FULLSTACK"),
        ("react", "리액트", "frontend", "프론트엔드"),
        ("typescript", "javascript", "next", "ui"),
        ("react", "next", "typescript", "javascript", "redux"),
    ),
    QueryCase(
        "edge_case",
        "쿠버네티스 platform",
        ("DEVOPS", "SRE", "SYSTEM_NETWORK"),
        ("kubernetes", "k8s", "쿠버네티스", "platform", "플랫폼"),
        ("devops", "인프라", "sre", "cloud", "클라우드"),
        ("kubernetes", "k8s", "docker", "aws", "terraform", "helm"),
    ),
    QueryCase(
        "edge_case",
        "AI 엔지니어",
        ("AI_ENGINEER", "ML_ENGINEER", "GENERATIVE_AI", "LLM", "COMPUTER_VISION", "AI_RESEARCHER"),
        ("ai", "ml", "llm", "머신러닝", "인공지능"),
        ("딥러닝", "생성형", "computer vision", "vision"),
        ("python", "pytorch", "tensorflow", "llm", "mlops", "opencv"),
    ),
)


TOKEN_PATTERN = re.compile(r"[a-z0-9+#.]+|[가-힣]+", re.IGNORECASE)


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Evaluate /jobs/search ranking quality using NDCG@limit and offline TF-IDF skill reranking."
    )
    parser.add_argument("--base-url", default=os.getenv("BASE_URL", "http://127.0.0.1:8081/api"))
    parser.add_argument("--limit", type=int, default=int(os.getenv("LIMIT", "10")))
    parser.add_argument("--fetch-limit", type=int, default=int(os.getenv("FETCH_LIMIT", "40")))
    parser.add_argument("--run-label", default=os.getenv("RUN_LABEL", "search-ndcg10"))
    parser.add_argument("--output-file", default=os.getenv("OUTPUT_FILE", ""))
    parser.add_argument("--summary-file", default=os.getenv("SUMMARY_FILE", ""))
    parser.add_argument(
        "--fetch-details",
        default=os.getenv("FETCH_DETAILS_FOR_TFIDF", "true").lower() == "true",
        action=argparse.BooleanOptionalAction,
        help="Fetch /jobs/{id} details for richer TF-IDF skill text.",
    )
    parser.add_argument("--min-ndcg", type=float, default=float(os.getenv("MIN_NDCG", "0.0")))
    parser.add_argument(
        "--fail-on-threshold",
        default=os.getenv("FAIL_ON_THRESHOLD", "false").lower() == "true",
        action=argparse.BooleanOptionalAction,
    )
    return parser.parse_args()


def normalize(value: Any) -> str:
    return str(value or "").casefold()


def contains_any(haystack: str, terms: tuple[str, ...]) -> bool:
    tokens = tokenize(haystack)
    return any(term_occurrences(tokens, term) > 0 for term in terms)


def tokenize(text: str) -> list[str]:
    return [token.casefold() for token in TOKEN_PATTERN.findall(text or "")]


def term_occurrences(tokens: list[str], term: str) -> int:
    term_tokens = tokenize(term)
    if not term_tokens:
        return 0

    width = len(term_tokens)
    return sum(
        1
        for index in range(len(tokens) - width + 1)
        if tokens[index:index + width] == term_tokens
    )


def api_get(base_url: str, path: str, params: dict[str, Any] | None = None) -> dict[str, Any]:
    parsed_base_url = urllib.parse.urlparse(base_url)
    if parsed_base_url.scheme not in {"http", "https"}:
        raise ValueError(f"base_url must use http or https scheme: {base_url}")

    url = f"{base_url.rstrip('/')}/{path.lstrip('/')}"
    if params:
        url = f"{url}?{urllib.parse.urlencode(params)}"

    request = urllib.request.Request(url, headers={"Accept": "application/json"})
    try:
        with urllib.request.urlopen(request, timeout=20) as response:
            return json.loads(response.read().decode("utf-8"))
    except urllib.error.HTTPError as exc:
        body = exc.read().decode("utf-8", errors="replace")
        raise RuntimeError(f"HTTP {exc.code} for {url}: {body}") from exc
    except urllib.error.URLError as exc:
        raise RuntimeError(f"Failed to call {url}: {exc}") from exc


def search_jobs(base_url: str, query: QueryCase, fetch_limit: int) -> list[dict[str, Any]]:
    payload = api_get(base_url, "/jobs/search", {"keyword": query.keyword, "limit": fetch_limit})
    if payload.get("success") is not True:
        raise RuntimeError(f"Search API failed for query={query.keyword!r}: {payload}")
    data = payload.get("data")
    if not isinstance(data, list):
        raise RuntimeError(f"Search API returned non-list data for query={query.keyword!r}: {payload}")
    return data


def fetch_job_detail(base_url: str, job_id: Any) -> dict[str, Any]:
    payload = api_get(base_url, f"/jobs/{job_id}")
    if payload.get("success") is not True:
        return {}
    data = payload.get("data")
    return data if isinstance(data, dict) else {}


def list_text(value: Any) -> list[str]:
    if value is None:
        return []
    if isinstance(value, str):
        return [value]
    if isinstance(value, (int, float, bool)):
        return [str(value)]
    if isinstance(value, list):
        texts: list[str] = []
        for item in value:
            texts.extend(list_text(item))
        return texts
    if isinstance(value, dict):
        texts = []
        for key, item in value.items():
            if key in {"description", "body", "title", "name", "displayName", "roleDetail", "type"}:
                texts.extend(list_text(item))
            elif key in {"skills", "experienceTags", "descriptionSections"}:
                texts.extend(list_text(item))
        return texts
    return []


def label_text(job: dict[str, Any], detail: dict[str, Any] | None = None) -> str:
    values = [
        normalize(job.get(key))
        for key in ("title", "companyName", "role", "careerLevel", "locationRegion", "locationCity")
    ]
    if detail:
        values.append(tfidf_text(job, detail))
    return " ".join(values)


def tfidf_text(job: dict[str, Any], detail: dict[str, Any]) -> str:
    base = [
        job.get("title"),
        job.get("companyName"),
        job.get("role"),
        job.get("careerLevel"),
        job.get("locationRegion"),
        job.get("locationCity"),
        detail.get("description"),
        detail.get("roleDetail"),
    ]
    base.extend(list_text(detail.get("skills")))
    base.extend(list_text(detail.get("experienceTags")))
    base.extend(list_text(detail.get("descriptionSections")))
    return " ".join(normalize(value) for value in base if value)


def relevance_grade(query: QueryCase, job: dict[str, Any], detail: dict[str, Any] | None = None) -> int:
    role = str(job.get("role") or "")
    text = label_text(job, detail)
    role_match = role in query.expected_roles
    strong_match = contains_any(text, query.strong_terms)
    support_match = contains_any(text, query.support_terms)
    critical_match = contains_any(text, query.critical_terms) if query.critical_terms else False

    if query.critical_terms:
        if role_match and critical_match:
            return 3
        if role_match and strong_match:
            return 2
        if critical_match:
            return 2
        if support_match:
            return 1
        return 0

    if role_match and strong_match:
        return 3
    if role_match or strong_match:
        return 2
    if support_match:
        return 1
    return 0


def dcg(grades: list[int]) -> float:
    return sum((2**grade - 1) / math.log2(index + 2) for index, grade in enumerate(grades))


def ndcg(grades: list[int], limit: int) -> float:
    actual = grades[:limit]
    ideal = sorted(grades, reverse=True)[:limit]
    ideal_dcg = dcg(ideal)
    return 0.0 if ideal_dcg == 0 else dcg(actual) / ideal_dcg


def tfidf_scores(texts: list[str], skill_terms: tuple[str, ...]) -> list[float]:
    documents = [tokenize(text) for text in texts]
    document_count = len(documents)
    if document_count == 0:
        return []

    document_frequency = {
        term: sum(1 for tokens in documents if term_occurrences(tokens, term) > 0)
        for term in skill_terms
    }

    scores: list[float] = []
    for tokens in documents:
        score = 0.0
        for term in skill_terms:
            if not term:
                continue
            term_frequency = term_occurrences(tokens, term)
            if term_frequency == 0:
                continue
            idf = math.log((document_count + 1) / (document_frequency[term] + 1)) + 1
            score += term_frequency * idf
        scores.append(score)
    return scores


def csv_row(run_label: str, ranking: str, query: QueryCase, rank: int, item: dict[str, Any]) -> dict[str, Any]:
    job = item["job"]
    return {
        "run_label": run_label,
        "ranking": ranking,
        "category": query.category,
        "query": query.keyword,
        "rank": rank,
        "original_rank": item["original_rank"],
        "id": job.get("id", ""),
        "source": job.get("source", ""),
        "title": job.get("title", ""),
        "company_name": job.get("companyName", ""),
        "role": job.get("role", ""),
        "career_level": job.get("careerLevel", ""),
        "location_region": job.get("locationRegion", ""),
        "location_city": job.get("locationCity", ""),
        "score": job.get("score", ""),
        "relevance_grade": item["relevance_grade"],
        "skill_tfidf_score": f"{item['skill_tfidf_score']:.6f}",
    }


def write_csv(path: str, rows: list[dict[str, Any]]) -> None:
    if not path:
        return
    output_path = Path(path)
    output_path.parent.mkdir(parents=True, exist_ok=True)
    with output_path.open("w", newline="", encoding="utf-8") as file:
        writer = csv.DictWriter(file, fieldnames=list(rows[0].keys()) if rows else [])
        writer.writeheader()
        writer.writerows(rows)


def write_summary(path: str, summary: dict[str, Any]) -> None:
    if not path:
        return
    output_path = Path(path)
    output_path.parent.mkdir(parents=True, exist_ok=True)
    output_path.write_text(json.dumps(summary, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def evaluate(args: argparse.Namespace) -> dict[str, Any]:
    if args.limit < 1 or args.limit > 100:
        raise ValueError(f"limit must be between 1 and 100: {args.limit}")
    if args.fetch_limit < args.limit or args.fetch_limit > 100:
        raise ValueError(f"fetch_limit must be between limit and 100: {args.fetch_limit}")

    csv_rows: list[dict[str, Any]] = []
    query_summaries: list[dict[str, Any]] = []
    metric_suffix = f"ndcg_at_{args.limit}"
    baseline_metric_key = f"baseline_{metric_suffix}"
    reranked_metric_key = f"skill_tfidf_{metric_suffix}"
    baseline_mean_metric_key = f"baseline_mean_{metric_suffix}"
    reranked_mean_metric_key = f"skill_tfidf_mean_{metric_suffix}"

    print(f"BASE_URL={args.base_url}")
    print(f"LIMIT={args.limit}")
    print(f"FETCH_LIMIT={args.fetch_limit}")
    print(f"FETCH_DETAILS_FOR_TFIDF={str(args.fetch_details).lower()}")
    print()

    for query in QUERY_CASES:
        jobs = search_jobs(args.base_url, query, args.fetch_limit)
        details_by_id: dict[Any, dict[str, Any]] = {}
        if args.fetch_details:
            for job in jobs:
                details_by_id[job.get("id")] = fetch_job_detail(args.base_url, job.get("id"))

        texts = [tfidf_text(job, details_by_id.get(job.get("id"), {})) for job in jobs]
        scores = tfidf_scores(texts, query.skill_terms)

        evaluated = []
        for index, job in enumerate(jobs):
            evaluated.append(
                {
                    "job": job,
                    "original_rank": index + 1,
                    "relevance_grade": relevance_grade(query, job, details_by_id.get(job.get("id"), {})),
                    "skill_tfidf_score": scores[index] if index < len(scores) else 0.0,
                }
            )

        baseline_grades = [item["relevance_grade"] for item in evaluated]
        baseline_ndcg = ndcg(baseline_grades, args.limit)
        reranked = sorted(evaluated, key=lambda item: (-item["skill_tfidf_score"], item["original_rank"]))
        reranked_grades = [item["relevance_grade"] for item in reranked]
        reranked_ndcg = ndcg(reranked_grades, args.limit)

        for rank, item in enumerate(evaluated[: args.limit], start=1):
            csv_rows.append(csv_row(args.run_label, "baseline", query, rank, item))
        for rank, item in enumerate(reranked[: args.limit], start=1):
            csv_rows.append(csv_row(args.run_label, "skill_tfidf_rerank", query, rank, item))

        query_summary = {
            "category": query.category,
            "query": query.keyword,
            "result_count": len(jobs),
            baseline_metric_key: round(baseline_ndcg, 4),
            reranked_metric_key: round(reranked_ndcg, 4),
            "delta": round(reranked_ndcg - baseline_ndcg, 4),
            "top_relevance_grades": baseline_grades[: args.limit],
            "reranked_top_relevance_grades": reranked_grades[: args.limit],
        }
        query_summaries.append(query_summary)
        print(
            "query='{query}' baseline_ndcg@{limit}={baseline:.4f} "
            "skill_tfidf_ndcg@{limit}={reranked:.4f} delta={delta:+.4f}".format(
                query=query.keyword,
                limit=args.limit,
                baseline=baseline_ndcg,
                reranked=reranked_ndcg,
                delta=reranked_ndcg - baseline_ndcg,
            )
        )

    baseline_average = sum(item[baseline_metric_key] for item in query_summaries) / len(query_summaries)
    reranked_average = sum(item[reranked_metric_key] for item in query_summaries) / len(query_summaries)
    category_summaries = []
    for category in sorted({query.category for query in QUERY_CASES}):
        items = [item for item in query_summaries if item["category"] == category]
        category_baseline_average = sum(item[baseline_metric_key] for item in items) / len(items)
        category_reranked_average = sum(item[reranked_metric_key] for item in items) / len(items)
        category_summaries.append(
            {
                "category": category,
                "query_count": len(items),
                baseline_mean_metric_key: round(category_baseline_average, 4),
                reranked_mean_metric_key: round(category_reranked_average, 4),
                "mean_delta": round(category_reranked_average - category_baseline_average, 4),
            }
        )
    summary = {
        "run_label": args.run_label,
        "base_url": args.base_url,
        "limit": args.limit,
        "fetch_limit": args.fetch_limit,
        "fetch_details_for_tfidf": args.fetch_details,
        "query_count": len(query_summaries),
        baseline_mean_metric_key: round(baseline_average, 4),
        reranked_mean_metric_key: round(reranked_average, 4),
        "mean_delta": round(reranked_average - baseline_average, 4),
        "categories": category_summaries,
        "queries": query_summaries,
    }

    write_csv(args.output_file, csv_rows)
    write_summary(args.summary_file, summary)

    print()
    print(f"### Search NDCG@{args.limit} Summary")
    print(f"query_count={summary['query_count']}")
    print(f"{baseline_mean_metric_key}={summary[baseline_mean_metric_key]:.4f}")
    print(f"{reranked_mean_metric_key}={summary[reranked_mean_metric_key]:.4f}")
    print(f"mean_delta={summary['mean_delta']:+.4f}")
    print("category_means=" + json.dumps(category_summaries, ensure_ascii=False))
    if args.output_file:
        print(f"csv_output={args.output_file}")
    if args.summary_file:
        print(f"summary_output={args.summary_file}")

    if args.fail_on_threshold and summary[baseline_mean_metric_key] < args.min_ndcg:
        raise RuntimeError(
            f"Search NDCG@{args.limit} is below threshold. "
            f"expected>={args.min_ndcg:.4f} actual={summary[baseline_mean_metric_key]:.4f}"
        )

    return summary


def main() -> int:
    try:
        evaluate(parse_args())
    except Exception as exc:
        print(f"Search NDCG evaluation failed: {exc}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
