/*
 * 팩트 야호 · Tailwind Play CDN 설정 (라이트 테마)
 *   <script src="https://cdn.tailwindcss.com"></script>
 *   <script src="/js/tailwind.config.js"></script>
 */
tailwind.config = {
  theme: {
    extend: {
      colors: {
        ink: '#16223A',          // 네이비 — 제목/헤딩
        'ink-2': '#334155',      // 본문 강조
        paper: '#F4F6FB',        // 페이지 배경
        line: '#E6EAF0',         // 카드 테두리
        'line-2': '#EEF2F7',     // 옅은 구분선
        brand: {
          DEFAULT: '#2563EB',    // 프라이머리 블루
          600: '#2563EB',
          700: '#1D4ED8',
          50: '#EFF4FF',
          100: '#DBE7FF',
        },
        seal: {
          DEFAULT: '#B7852A',    // 공식 출처 골드
          50: '#FBF5E6',
          border: '#F0E0BC',
        },
      },
      fontFamily: {
        sans: [
          'Pretendard', 'Pretendard Variable', '-apple-system',
          'BlinkMacSystemFont', 'system-ui', 'Segoe UI', 'Roboto',
          'Helvetica Neue', 'Arial', 'Noto Sans KR', 'sans-serif',
        ],
      },
      boxShadow: {
        card: '0 1px 2px rgba(18,35,59,.03), 0 12px 32px -20px rgba(18,35,59,.20)',
        pop: '0 20px 50px -24px rgba(18,35,59,.35)',
      },
      borderRadius: { xl2: '1rem' },
      maxWidth: { page: '1120px' },
    },
  },
};
