"use client";

import { useState, useRef, useCallback, useEffect } from "react";
import { Search, StopCircle, ExternalLink, Loader2, Globe, Sparkles, BookOpen } from "lucide-react";

const API = process.env.NEXT_PUBLIC_API_URL || "http://localhost:8080";

type Mode = "fast" | "balanced" | "deep";

interface Source {
  title: string;
  url: string;
  domain: string;
  description?: string;
  faviconUrl?: string;
}

interface FinalSource {
  number: number;
  title: string;
  url: string;
  domain: string;
}

export default function Home() {
  const [query, setQuery] = useState("");
  const [mode, setMode] = useState<Mode>("balanced");
  const [searching, setSearching] = useState(false);
  const [status, setStatus] = useState("");
  const [sources, setSources] = useState<Source[]>([]);
  const [answer, setAnswer] = useState("");
  const [finalSources, setFinalSources] = useState<FinalSource[]>([]);
  const [error, setError] = useState("");
  const [taskId, setTaskId] = useState<string | null>(null);
  const esRef = useRef<EventSource | null>(null);
  const answerEnd = useRef<HTMLDivElement>(null);

  const stop = useCallback(async () => {
    if (taskId) {
      try { await fetch(`${API}/api/search/${taskId}/cancel`, { method: "POST" }); } catch {}
    }
    esRef.current?.close();
    esRef.current = null;
    setSearching(false);
    setStatus("Stopped");
  }, [taskId]);

  const start = async () => {
    const q = query.trim();
    if (!q || searching) return;

    setSearching(true);
    setStatus("Starting...");
    setSources([]);
    setAnswer("");
    setFinalSources([]);
    setError("");
    setTaskId(null);

    try {
      const res = await fetch(`${API}/api/search`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ query: q, mode }),
      });
      if (!res.ok) {
        const body = await res.json().catch(() => ({}));
        throw new Error(body.error || "Failed to start search");
      }
      const data = await res.json();
      setTaskId(data.id);

      const es = new EventSource(
        `${API}/api/search/${data.id}/events?query=${encodeURIComponent(q)}&mode=${mode}`
      );
      esRef.current = es;

      es.onmessage = (ev) => {
        try {
          const e = JSON.parse(ev.data);
          handle(e);
        } catch {}
      };
      es.onerror = () => {
        es.close();
        esRef.current = null;
        setSearching(false);
        if (!answer) setError("Connection lost. Please try again.");
      };
    } catch (err: any) {
      setError(err.message || "Could not start search");
      setSearching(false);
    }
  };

  const handle = (e: any) => {
    switch (e.type) {
      case "STATUS":
        if (e.message) setStatus(e.message);
        break;
      case "SOURCE":
        if (e.url) {
          setSources((prev) => {
            if (prev.some((s) => s.url === e.url)) return prev;
            return [...prev, {
              title: e.title || "Untitled",
              url: e.url,
              domain: e.domain || "",
              description: e.description,
              faviconUrl: e.faviconUrl,
            }];
          });
          if (e.sourceCount) setStatus(`Searching... ${e.sourceCount} sources found`);
        }
        break;
      case "READING":
        if (e.title) setStatus(`Reading: ${e.title}`);
        break;
      case "TOKEN":
        if (e.token) setAnswer((a) => a + e.token);
        break;
      case "DONE":
        setSearching(false);
        setStatus(e.message || "Complete");
        if (e.answer) setAnswer(e.answer);
        if (e.sources) setFinalSources(e.sources);
        esRef.current?.close();
        esRef.current = null;
        break;
      case "ERROR":
        setError(e.message || "An error occurred");
        break;
    }
  };

  useEffect(() => {
    answerEnd.current?.scrollIntoView({ behavior: "smooth" });
  }, [answer]);

  const renderAnswer = (text: string) => {
    const parts = text.split(/(\[\d+\])/g);
    return parts.map((part, i) => {
      const m = part.match(/\[(\d+)\]/);
      if (m) {
        const n = parseInt(m[1], 10);
        const src = finalSources.find((s) => s.number === n) || sources[n - 1];
        if (src) {
          return (
            <a key={i} href={src.url} target="_blank" rel="noopener noreferrer" className="cite" title={src.title}>
              [{n}]
            </a>
          );
        }
      }
      return <span key={i} className="whitespace-pre-wrap">{part}</span>;
    });
  };

  const showHome = !searching && !answer && !error;

  return (
    <div className="min-h-screen flex flex-col">
      {/* Header */}
 <header className="border-b border-zinc-800/80 sticky top-0 z-40 bg-zinc-950/95 backdrop-blur">
  <div className="max-w-6xl mx-auto px-4 h-16 flex items-center justify-between">

    {/* AKHIL AI SEARCH BRAND */}
    <div className="flex items-center gap-3">

      {/* Green 3D Logo */}
      <div className="relative w-11 h-11 rounded-xl bg-gradient-to-br from-emerald-200 via-emerald-500 to-emerald-900 flex items-center justify-center border border-emerald-300/50 shadow-[inset_0_2px_4px_rgba(255,255,255,0.35),inset_0_-4px_6px_rgba(0,0,0,0.25),0_6px_20px_rgba(16,185,129,0.4)]">
        {/* 3D highlight */}
        <div className="absolute top-1 left-1 right-1 h-3 rounded-t-lg bg-white/20 blur-[1px]" />

        {/* A */}
        <span className="relative z-10 text-white font-black text-2xl italic drop-shadow-[0_3px_2px_rgba(0,0,0,0.45)]">
          A
        </span>

      </div>

      {/* Brand text */}
      <div className="flex flex-col">
        <span className="font-bold text-[16px] tracking-tight text-white leading-tight">
          AKHIL AI SEARCH
        </span>

        <span className="text-[10px] font-medium tracking-[0.16em] text-emerald-400 leading-tight mt-0.5">
          INTELLIGENT WEB RESEARCH
        </span>
      </div>

    </div>

    {/* Status */}
    <div className="hidden sm:flex items-center gap-2.5 text-xs text-zinc-400">
      <span className="relative flex h-2 w-2">
        <span className="absolute inline-flex h-full w-full rounded-full bg-emerald-400 opacity-60 animate-ping" />
        <span className="relative inline-flex h-2 w-2 rounded-full bg-emerald-400 shadow-[0_0_10px_rgba(52,211,153,0.8)]" />
      </span>

      <span className="font-medium tracking-wide">
        REAL-TIME WEB SEARCH
      </span>
    </div>

  </div>
</header>

      <main className="flex-1 flex flex-col lg:flex-row max-w-6xl w-full mx-auto">
        {/* Main column */}
        <div className="flex-1 min-w-0 flex flex-col">
          {showHome && (
            <div className="flex-1 flex flex-col items-center justify-center px-4 py-20">
              <h1 className="text-4xl sm:text-5xl font-bold tracking-tight mb-3">
               AKHIL AI Search
              </h1>
              <p className="text-zinc-400 text-center max-w-md mb-10 text-[15px]">
                Ask anything. Get answers grounded in real web sources with citations.
              </p>

              <div className="w-full max-w-xl">
                <div className="relative">
                  <input
                    value={query}
                    onChange={(e) => setQuery(e.target.value)}
                    onKeyDown={(e) => e.key === "Enter" && start()}
                    placeholder="What do you want to search?"
                    className="w-full bg-zinc-900 border border-zinc-700 rounded-2xl px-5 py-4 pr-28 text-[15px] placeholder:text-zinc-500 focus:outline-none focus:ring-2 focus:ring-brand-500/40 focus:border-brand-500/50 transition"
                    autoFocus
                  />
                  <button
                    onClick={start}
                    disabled={!query.trim()}
                    className="absolute right-2 top-1/2 -translate-y-1/2 bg-brand-600 hover:bg-brand-500 disabled:opacity-40 text-white text-sm font-medium px-4 py-2 rounded-xl flex items-center gap-1.5 transition"
                  >
                    <Search className="w-4 h-4" />
                    Search
                  </button>
                </div>

                <div className="mt-4 flex justify-center gap-1.5">
                  {(["fast", "balanced", "deep"] as Mode[]).map((m) => (
                    <button
                      key={m}
                      onClick={() => setMode(m)}
                      className={`px-3.5 py-1.5 rounded-lg text-xs font-medium transition ${
                        mode === m
                          ? "bg-brand-600/20 text-brand-300 border border-brand-500/40"
                          : "text-zinc-500 hover:text-zinc-300 border border-transparent"
                      }`}
                    >
                      {m === "fast" ? "Fast" : m === "balanced" ? "Balanced" : "Deep"}
                    </button>
                  ))}
                </div>
              </div>
            </div>
          )}

          {(searching || answer || error) && (
            <div className="flex-1 flex flex-col px-4 py-6 overflow-hidden">
              <div className="flex items-start justify-between gap-3 mb-4">
                <div>
                  <p className="text-xs text-zinc-500 mb-0.5">Question</p>
                  <p className="text-base font-medium text-zinc-100">{query}</p>
                </div>
                {searching && (
                  <button
                    onClick={stop}
                    className="shrink-0 flex items-center gap-1.5 text-sm text-red-400 hover:text-red-300 border border-red-500/25 px-3 py-1.5 rounded-lg transition"
                  >
                    <StopCircle className="w-4 h-4" />
                    Stop
                  </button>
                )}
              </div>

              {status && (
                <div className="flex items-center gap-2 text-sm text-zinc-400 mb-4">
                  {searching ? (
                    <Loader2 className="w-4 h-4 animate-spin text-brand-400" />
                  ) : (
                    <Sparkles className="w-4 h-4 text-brand-400" />
                  )}
                  <span>{status}</span>
                </div>
              )}

              {error && (
                <div className="mb-4 p-3 rounded-xl bg-red-500/10 border border-red-500/20 text-red-300 text-sm">
                  {error}
                </div>
              )}

              {answer && (
                <div className="flex-1 overflow-y-auto answer-body text-[15px] text-zinc-200 leading-relaxed pb-6">
                  {renderAnswer(answer)}
                  <div ref={answerEnd} />

                  {finalSources.length > 0 && (
                    <div className="mt-8 pt-5 border-t border-zinc-800">
                      <h3 className="text-sm font-semibold text-zinc-300 mb-3 flex items-center gap-2">
                        <BookOpen className="w-4 h-4" />
                        Sources
                      </h3>
                      <div className="space-y-2">
                        {finalSources.map((s) => (
                          <a
                            key={s.number}
                            href={s.url}
                            target="_blank"
                            rel="noopener noreferrer"
                            className="flex items-start gap-3 p-3 rounded-xl bg-zinc-900/80 hover:bg-zinc-800/80 border border-zinc-800 transition group"
                          >
                            <span className="text-xs font-mono text-brand-400 mt-0.5">[{s.number}]</span>
                            <div className="flex-1 min-w-0">
                              <p className="text-sm font-medium text-zinc-200 group-hover:text-white truncate">{s.title}</p>
                              <p className="text-xs text-zinc-500 truncate">{s.domain}</p>
                            </div>
                            <ExternalLink className="w-3.5 h-3.5 text-zinc-600 group-hover:text-brand-400 shrink-0 mt-1" />
                          </a>
                        ))}
                      </div>
                    </div>
                  )}
                </div>
              )}

              {!searching && (answer || error) && (
                <div className="pt-4 border-t border-zinc-800/80 mt-auto">
                  <button
                    onClick={() => {
                      setAnswer(""); setSources([]); setFinalSources([]);
                      setStatus(""); setError(""); setQuery("");
                    }}
                    className="text-sm text-brand-400 hover:text-brand-300 flex items-center gap-1.5"
                  >
                    <Search className="w-3.5 h-3.5" />
                    New search
                  </button>
                </div>
              )}
            </div>
          )}
        </div>

        {/* Source panel */}
        {(searching || sources.length > 0) && (
          <aside className="w-full lg:w-80 border-t lg:border-t-0 lg:border-l border-zinc-800 bg-zinc-900/40 flex flex-col max-h-[45vh] lg:max-h-none">
            <div className="px-4 py-3 border-b border-zinc-800 flex items-center gap-2">
              <Globe className="w-4 h-4 text-brand-400" />
              <span className="text-sm font-medium">Sources</span>
              <span className="text-xs text-zinc-500 ml-auto">{sources.length}</span>
            </div>
            <div className="flex-1 overflow-y-auto p-3 space-y-2">
              {sources.length === 0 && searching && (
                <div className="text-center text-zinc-500 text-sm py-10">
                  <Loader2 className="w-5 h-5 animate-spin mx-auto mb-2 text-brand-400" />
                  Finding sources...
                </div>
              )}
              {sources.map((s, i) => (
                <a
                  key={s.url + i}
                  href={s.url}
                  target="_blank"
                  rel="noopener noreferrer"
                  className="block p-3 rounded-xl bg-zinc-900/60 hover:bg-zinc-800/80 border border-zinc-800/80 transition group"
                >
                  <div className="flex items-start gap-2.5">
                    {s.faviconUrl ? (
                      <img src={s.faviconUrl} alt="" className="w-4 h-4 mt-0.5 rounded-sm" onError={(e) => { (e.target as HTMLImageElement).style.display = "none"; }} />
                    ) : (
                      <div className="w-4 h-4 mt-0.5 rounded-sm bg-zinc-700" />
                    )}
                    <div className="flex-1 min-w-0">
                      <p className="text-sm font-medium text-zinc-200 group-hover:text-white line-clamp-2">{s.title}</p>
                      <p className="text-xs text-zinc-500 mt-0.5 truncate">{s.domain}</p>
                      {s.description && (
                        <p className="text-xs text-zinc-600 mt-1 line-clamp-2">{s.description}</p>
                      )}
                    </div>
                    <ExternalLink className="w-3 h-3 text-zinc-600 group-hover:text-brand-400 shrink-0 mt-1" />
                  </div>
                </a>
              ))}
            </div>
          </aside>
        )}
      </main>

      <footer className="border-t border-zinc-800/60 py-3 text-center text-xs text-zinc-600">
        Searches a portion of the public web via configured providers. Not a complete internet index.
      </footer>
    </div>
  );
}
