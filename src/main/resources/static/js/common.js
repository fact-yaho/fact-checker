/* ==========================================================================
   팩트 야호 · 공통 스크립트 (common.js)
   - 인증 세션(localStorage)
   - enum 라벨/색/아이콘 매핑 (Verdict / Stance / EvidenceSourceType / AnalysisStatus)
   - 라이트 네비게이션 · fetch 래퍼 · 렌더 헬퍼 · 결과 stash · 토스트
   모든 API는 같은 오리진에서 서빙되므로 상대경로(/api/v1/...)를 사용합니다.
   ========================================================================== */
(function (global) {
  "use strict";

  /* ------------------------------------------------------------------ 인증 */
  const TOKEN_KEY = "token";
  const REFRESH_KEY = "refreshToken";
  const TYPE_KEY = "tokenType";

  const Auth = {
    get token() { return localStorage.getItem(TOKEN_KEY); },
    get isLoggedIn() { return !!localStorage.getItem(TOKEN_KEY); },
    save(loginResponse) {
      if (!loginResponse) return;
      const access = loginResponse.accessToken || loginResponse.access_token || loginResponse.token;
      if (access) localStorage.setItem(TOKEN_KEY, access);
      if (loginResponse.refreshToken) localStorage.setItem(REFRESH_KEY, loginResponse.refreshToken);
      if (loginResponse.tokenType) localStorage.setItem(TYPE_KEY, loginResponse.tokenType);
    },
    captureUrlToken() {
      const p = new URLSearchParams(location.search);
      const t = p.get("token");
      if (t) {
        localStorage.setItem(TOKEN_KEY, t);
        history.replaceState({}, document.title, location.pathname);
      }
    },
    clear() {
      localStorage.removeItem(TOKEN_KEY);
      localStorage.removeItem(REFRESH_KEY);
      localStorage.removeItem(TYPE_KEY);
    },
    header() {
      const t = localStorage.getItem(TOKEN_KEY);
      if (!t) return {};
      const type = localStorage.getItem(TYPE_KEY) || "Bearer";
      return { Authorization: `${type} ${t}` };
    },
    logout() {
      this.clear();
      toast("로그아웃했습니다.");
      setTimeout(() => (location.href = "/"), 500);
    },
    requireLogin(returnUrl) {
      if (this.isLoggedIn) return true;
      const back = encodeURIComponent(returnUrl || location.pathname + location.search);
      location.href = `/login?returnUrl=${back}`;
      return false;
    },
  };

  /* ---------------------------------------------------------------- fetch */
  async function api(path, options = {}) {
    const opts = Object.assign({ headers: {} }, options);
    opts.headers = Object.assign(
      { "Content-Type": "application/json" }, Auth.header(), opts.headers
    );
    return fetch(path, opts);
  }
  async function apiJson(path, options) {
    const res = await api(path, options);
    if (!res.ok) {
      const err = new Error(await safeMessage(res));
      err.status = res.status;
      throw err;
    }
    const text = await res.text();
    return text ? JSON.parse(text) : null;
  }
  async function safeMessage(res) {
    try {
      const raw = await res.text();
      // 5xx = 서버 내부 오류. 원문(스택·외부 서비스 응답 등)을 화면에 노출하지 않는다.
      // 디버깅용으로 콘솔에만 남긴다.
      if (res.status >= 500) {
        if (raw) console.error(`[${res.status}]`, raw);
        return "일시적인 오류가 발생했어요. 잠시 후 다시 시도해 주세요.";
      }
      if (!raw) return `요청이 실패했어요 (${res.status})`;
      try { const j = JSON.parse(raw); return j.message || j.error || raw; }
      catch { return raw; }
    } catch { return `요청이 실패했어요 (${res.status})`; }
  }

  /* --------------------------------------- 결과 stash (POST 응답 → 결과 페이지) */
  // POST /fact-checks 가 AnalysisResultDetailResponse 전체를 주면, 재조회 없이 렌더하기 위해
  // sessionStorage 에 잠시 담아둔다. (비회원도 방금 만든 결과를 볼 수 있게)
  const Result = {
    key: (id) => `factyaho:result:${id}`,
    stash(detail) {
      if (!detail || !detail.id) return;
      try { sessionStorage.setItem(this.key(detail.id), JSON.stringify(detail)); } catch (e) {}
    },
    take(id) {
      try {
        const raw = sessionStorage.getItem(this.key(id));
        return raw ? JSON.parse(raw) : null;
      } catch (e) { return null; }
    },
  };

  /* -------------------------------------------------- enum 라벨/색/아이콘 */
  const VERDICT = {
    CONSISTENT:             { label: "공식자료와 일치",   tone: "#059669", soft: "#ECFDF5", icon: "ti-check" },
    PARTIALLY_CONSISTENT:   { label: "대체로 일치",       tone: "#65A30D", soft: "#F7FEE7", icon: "ti-check" },
    UNCERTAIN:              { label: "판단 유보",         tone: "#D97706", soft: "#FFFBEB", icon: "ti-question-mark" },
    PARTIALLY_INCONSISTENT: { label: "부분 불일치",       tone: "#EA580C", soft: "#FFF7ED", icon: "ti-alert-triangle" },
    INCONSISTENT:           { label: "공식자료와 불일치", tone: "#DC2626", soft: "#FEF2F2", icon: "ti-x" },
    INSUFFICIENT:           { label: "근거 부족",         tone: "#64748B", soft: "#F1F5F9", icon: "ti-minus" },
    OUT_OF_SCOPE:           { label: "검증 대상 아님",     tone: "#78716C", soft: "#F5F5F4", icon: "ti-ban" },
  };
  const VERDICT_FALLBACK = { label: "판정 없음", tone: "#64748B", soft: "#F1F5F9", icon: "ti-help" };

  const STANCE = {
    SUPPORTS:     { label: "뒷받침",   tone: "#059669", soft: "#ECFDF5" },
    CONTRADICTS:  { label: "반박",     tone: "#DC2626", soft: "#FEF2F2" },
    PARTIAL:      { label: "부분 일치", tone: "#EA580C", soft: "#FFF7ED" },
    INSUFFICIENT: { label: "불충분",   tone: "#64748B", soft: "#F1F5F9" },
    IRRELEVANT:   { label: "무관",     tone: "#94A3B8", soft: "#F1F5F9" },
  };
  const STANCE_FALLBACK = { label: "미분류", tone: "#94A3B8", soft: "#F1F5F9" };

  const EVIDENCE_SOURCE = {
    PRESS_RELEASE:  { label: "보도자료",      official: true },
    SPEECH:         { label: "연설문",        official: true },
    BRIEFING:       { label: "브리핑",        official: true },
    POLICY_DOCUMENT:{ label: "정책 문서",     official: true },
    PUBLIC_API:     { label: "공공데이터 API", official: true },
    NEWS:           { label: "뉴스",          official: false },
    ETC:            { label: "기타",          official: false },
  };
  const ANALYSIS_STATUS = { COMPLETED: "분석 완료", FAILED: "분석 실패" };
  const BREAKDOWN_LABELS = {
    officialConsistencyScore: "공식자료 일치도",
    evidenceRelevanceScore:  "근거 관련도",
    evidenceSufficiencyScore:"근거 충분성",
    recencyScore:            "최신성",
    contradictionPenalty:    "모순 근거 패널티",
  };

  const verdictInfo = (v) => VERDICT[v] || VERDICT_FALLBACK;
  const stanceInfo  = (s) => STANCE[s]  || STANCE_FALLBACK;

  // 점수를 매기지 않는 판정(근거 부족 / 검증 대상 아님)
  const UNSCORED_VERDICTS = new Set(["INSUFFICIENT", "OUT_OF_SCOPE"]);
  const isUnscored = (v) => UNSCORED_VERDICTS.has(v);

  /* ------------------------------------------------------------- 포맷터 */
  function esc(s) {
    if (s == null) return "";
    return String(s)
      .replaceAll("&", "&amp;").replaceAll("<", "&lt;").replaceAll(">", "&gt;")
      .replaceAll('"', "&quot;").replaceAll("'", "&#39;");
  }
  function scoreText(v) {
    if (v == null || isNaN(v)) return "—";
    return Math.round(v);
  }
  function scorePct(v) {
    if (v == null || isNaN(v)) return 0;
    return Math.max(0, Math.min(100, v));
  }
  function metricText(v) {
    if (v == null || isNaN(v)) return null;
    return v <= 1 ? Number(v).toFixed(2) : String(Math.round(v));
  }
  function fmtDate(iso) {
    if (!iso) return "";
    const d = new Date(iso); if (isNaN(d)) return "";
    const p = (n) => String(n).padStart(2, "0");
    return `${d.getFullYear()}.${p(d.getMonth() + 1)}.${p(d.getDate())}`;
  }
  function fmtDateTime(iso) {
    if (!iso) return "";
    const d = new Date(iso); if (isNaN(d)) return "";
    const p = (n) => String(n).padStart(2, "0");
    return `${d.getFullYear()}.${p(d.getMonth() + 1)}.${p(d.getDate())} ${p(d.getHours())}:${p(d.getMinutes())}`;
  }

  /* -------------------------------------------------------- 렌더 헬퍼 */
  // 판정 배지(인라인 pill) — 색점 + 라벨
  function verdictChip(v) {
    const i = verdictInfo(v);
    return `<span class="inline-flex items-center gap-1.5 text-xs font-bold rounded-full px-2.5 py-1" style="color:${i.tone};background:${i.soft}">
      <span class="w-1.5 h-1.5 rounded-full" style="background:${i.tone}"></span>${esc(i.label)}</span>`;
  }
  function stanceChip(s) {
    const i = stanceInfo(s);
    return `<span class="text-xs font-bold rounded-full px-2 py-0.5" style="color:${i.tone};background:${i.soft}">${esc(i.label)}</span>`;
  }
  // 판정 아이콘 원형 배지 (결과 헤더용). size: px
  function verdictIconBadge(v, size) {
    const i = verdictInfo(v);
    const s = size || 60;
    return `<span class="grid place-items-center rounded-full flex-none text-white" style="width:${s}px;height:${s}px;background:${i.tone};font-size:${Math.round(s*0.5)}px"><i class="ti ${i.icon}" aria-hidden="true"></i></span>`;
  }
  // 신뢰도 점수 바
  function scoreBarHtml(finalScore, verdict) {
    const i = verdictInfo(verdict);
    const pct = scorePct(finalScore);
    return `<div>
      <div class="text-[11px] font-bold tracking-wider text-slate-400 uppercase mb-1.5">신뢰도 점수</div>
      <div class="flex items-baseline gap-1.5 mb-2"><span class="tnum text-4xl font-extrabold" style="color:${i.tone}">${scoreText(finalScore)}</span><span class="text-base font-bold text-slate-300">/ 100</span></div>
      <div class="h-2.5 rounded-full overflow-hidden" style="background:${i.soft}"><div class="bar-fill h-full rounded-full" style="width:${pct}%;background:${i.tone}"></div></div>
    </div>`;
  }
  // 공식 출처 골드 배지
  function officialBadge() {
    return `<span class="inline-flex items-center gap-1 text-[10.5px] font-bold rounded-full px-2 py-0.5" style="color:#B7852A;background:#FBF5E6;border:1px solid #F0E0BC"><i class="ti ti-award" aria-hidden="true"></i>공식</span>`;
  }

  /* --------------------------------------------------------- 네비게이션 */
  function renderNav(active) {
    const host = document.getElementById("nav");
    if (!host) return;
    const loggedIn = Auth.isLoggedIn;
    const item = (href, label, key) =>
      `<a href="${href}" class="px-3 py-2 text-sm font-semibold rounded-lg transition ${
        active === key ? "text-brand bg-brand-50" : "text-slate-500 hover:text-ink hover:bg-slate-50"
      }">${label}</a>`;

    const right = loggedIn
      ? `${item("/history", "기록", "history")}
         <a href="/mypage" class="px-3 py-2 text-sm font-semibold rounded-lg text-slate-500 hover:text-ink hover:bg-slate-50 transition">마이페이지</a>
         <button type="button" data-action="logout" class="px-3 py-2 text-sm font-semibold rounded-lg text-slate-500 hover:text-ink hover:bg-slate-50 transition">로그아웃</button>`
      : `${item("/history", "기록", "history")}
         <a href="/login" class="px-3.5 py-2 text-sm font-semibold rounded-lg text-slate-600 border border-line hover:border-slate-300 transition">로그인</a>
         <a href="/signup" class="px-4 py-2 text-sm font-bold rounded-lg bg-brand text-white hover:bg-brand-700 transition">회원가입</a>`;

    host.innerHTML = `
      <div class="mx-auto max-w-page px-5 h-16 flex items-center justify-between">
        <a href="/" class="flex items-center gap-2.5">
          <span class="grid place-items-center w-9 h-9 rounded-[10px] bg-brand text-white text-[19px]"><i class="ti ti-shield-check" aria-hidden="true"></i></span>
          <span class="leading-tight">
            <span class="block font-extrabold text-ink text-[16px] tracking-tight">외교 팩트체커</span>
            <span class="hidden sm:block text-[11px] text-slate-400 -mt-0.5">공공데이터 기반 신뢰도 검증</span>
          </span>
        </a>
        <nav class="flex items-center gap-1">${right}</nav>
      </div>`;

    const logoutBtn = host.querySelector('[data-action="logout"]');
    if (logoutBtn) logoutBtn.addEventListener("click", () => Auth.logout());
  }

  /* -------------------------------------------------------------- 토스트 */
  let toastEl;
  function toast(msg, isError) {
    if (!toastEl) { toastEl = document.createElement("div"); toastEl.className = "toast"; document.body.appendChild(toastEl); }
    toastEl.textContent = msg;
    toastEl.classList.toggle("err", !!isError);
    toastEl.classList.add("show");
    clearTimeout(toastEl._t);
    toastEl._t = setTimeout(() => toastEl.classList.remove("show"), 3200);
  }

  /* --------------------------------------------------------------- 모달 */
  function isHttpUrl(u) {
    return typeof u === "string" && /^https?:\/\//i.test(u.trim());
  }
  function modalEsc(e) { if (e.key === "Escape") closeModal(); }
  function closeModal() {
    const m = document.getElementById("yaho-modal");
    if (m) m.remove();
    document.removeEventListener("keydown", modalEsc);
    document.body.style.overflow = "";
  }
  function showModal(opts) {
    const o = opts || {};
    closeModal();
    const overlay = document.createElement("div");
    overlay.id = "yaho-modal";
    overlay.className = "fixed inset-0 z-[80] overflow-y-auto";
    overlay.style.background = "rgba(22,34,58,.5)";
    overlay.innerHTML = `
      <div class="min-h-full flex items-start sm:items-center justify-center p-4 sm:p-6">
        <div class="w-full max-w-2xl bg-white rounded-xl2 shadow-pop flex flex-col max-h-[86vh]" role="dialog" aria-modal="true">
          <div class="flex items-start justify-between gap-3 px-5 sm:px-6 py-4 border-b border-line">
            <div class="min-w-0">${o.title || ""}</div>
            <button type="button" data-close class="flex-none -mr-1 grid place-items-center w-8 h-8 rounded-lg text-slate-400 hover:text-ink hover:bg-slate-100 transition" aria-label="닫기"><i class="ti ti-x text-[18px]" aria-hidden="true"></i></button>
          </div>
          <div class="px-5 sm:px-6 py-5 overflow-y-auto">${o.bodyHtml || ""}</div>
        </div>
      </div>`;
    overlay.addEventListener("click", (e) => {
      if (e.target === overlay || e.target.closest("[data-close]")) closeModal();
    });
    document.addEventListener("keydown", modalEsc);
    document.body.appendChild(overlay);
    document.body.style.overflow = "hidden";
  }

  /* --------------------------------------------------------------- export */
  global.Yaho = {
    Auth, api, apiJson, safeMessage, Result,
    VERDICT, STANCE, EVIDENCE_SOURCE, ANALYSIS_STATUS, BREAKDOWN_LABELS,
    verdictInfo, stanceInfo, isUnscored,
    esc, scoreText, scorePct, metricText, fmtDate, fmtDateTime,
    verdictChip, stanceChip, verdictIconBadge, scoreBarHtml, officialBadge,
    renderNav, toast, showModal, closeModal, isHttpUrl,
  };
})(window);
