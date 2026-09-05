# AI Search

Real-time AI-powered web research engine.

Ask a question → the system searches the public web (Tavily), reads pages (Jina Reader), analyzes with OpenAI, and streams a grounded answer with **real clickable citations**.

> This application searches a **portion of the publicly accessible web** through configured search providers. It does **not** search every website on the internet.

---

## Features

- Real web search via Tavily API
- Multi-query planning for complex questions
- Webpage extraction via Jina AI Reader
- Live source panel (title, domain, favicon, link)
- Streaming answer (SSE) with inline citations
- Search modes: Fast / Balanced / Deep
- Stop research at any time
- SSRF protection, timeouts, graceful error handling
- Clean, modern dark UI

---

## Architecture

```
User question
    → Query planning (OpenAI)
    → Web search (Tavily)
    → Source display (live)
    → Page reading (Jina)
    → Answer generation (OpenAI stream)
    → Citations → original URLs
```

**Backend:** Spring Boot 3.3 + Java 21  
**Frontend:** Next.js 14 + React + Tailwind  
**Search:** Tavily  
**LLM:** OpenAI  
**Reader:** Jina AI Reader  

---

## Requirements

- Java 21+
- Node.js 18+
- OpenAI API key
- Tavily API key ([tavily.com](https://tavily.com))

---

## Environment Variables

Copy `.env.example` to `.env`:

```bash
cp .env.example .env
```

| Variable | Required | Description |
|----------|----------|-------------|
| `OPENAI_API_KEY` | Yes | OpenAI key |
| `TAVILY_API_KEY` | Yes | Tavily key |
| `FRONTEND_URL` | No | CORS origin (default `http://localhost:3000`) |
| `NEXT_PUBLIC_API_URL` | No | Backend URL for frontend (default `http://localhost:8080`) |
| `PORT` | No | Backend port (default `8080`, required for Render) |

**Never commit real keys.** Keys stay on the backend only.

---

## Local Setup

### 1. Backend

```bash
cd backend
export OPENAI_API_KEY=sk-...
export TAVILY_API_KEY=tvly-...
export FRONTEND_URL=http://localhost:3000

mvn spring-boot:run
```

Health: http://localhost:8080/api/health

### 2. Frontend

```bash
cd frontend
npm install
export NEXT_PUBLIC_API_URL=http://localhost:8080
npm run dev
```

Open: http://localhost:3000

---

## Docker

```bash
cp .env.example .env
# edit .env with your keys

docker compose up --build
```

- Frontend: http://localhost:3000  
- Backend: http://localhost:8080  

---

## API

| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/api/search` | Start research `{ "query": "...", "mode": "balanced" }` |
| `GET`  | `/api/search/{id}/events?query=...&mode=...` | SSE event stream |
| `POST` | `/api/search/{id}/cancel` | Cancel research |
| `GET`  | `/api/health` | Health check |

### Event types

`STATUS` · `SOURCE` · `READING` · `TOKEN` · `DONE` · `ERROR`

---

## Search Modes

| Mode | Queries | Sources | Pages |
|------|---------|---------|-------|
| Fast | 1 | 5 | 3 |
| Balanced | 2 | 8 | 5 |
| Deep | 4 | 12 | 8 |

---

## Deployment

### Frontend → Vercel

1. Push repo to GitHub.
2. Import the **frontend** folder as a Vercel project (set Root Directory to `frontend`).
3. Add environment variable:
   ```
   NEXT_PUBLIC_API_URL=https://your-backend.onrender.com
   ```
4. Deploy.

### Backend → Render

1. Create a **Web Service** from the same repo.
2. Root Directory: `backend`
3. Build Command: `mvn -DskipTests package`
4. Start Command: `java -jar target/ai-search-1.0.0.jar`
5. Add environment variables:
   - `OPENAI_API_KEY`
   - `TAVILY_API_KEY`
   - `FRONTEND_URL` = your Vercel URL (e.g. `https://your-app.vercel.app`)
   - `PORT` is provided automatically by Render
6. Deploy and verify `https://your-backend.onrender.com/api/health`

Then update Vercel `NEXT_PUBLIC_API_URL` to the Render URL and redeploy the frontend.

---

## Security

- API keys never sent to the browser
- CORS restricted to configured origin
- SSRF protection (blocks private IPs / non-http schemes)
- URL validation, page size limits, timeouts
- Input length validation

---

## Limitations

- Relies on Tavily’s index and Jina’s ability to extract public pages
- Sites behind login, CAPTCHA, or paywalls are skipped
- Rate limits of OpenAI and Tavily apply
- Does not claim to search the entire internet

---

## License

MIT
