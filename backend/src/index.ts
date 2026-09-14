// Checklist API — Cloudflare Worker + D1
//
// Endpoints (all require header: Authorization: Bearer <API_TOKEN>):
//   POST   /notes            { text: string }              -> create note, auto-creates category/subcategory
//   GET    /notes             ?since=<ms>&includeDeleted=1  -> list notes (for sync) or full browse
//   PATCH  /notes/:id         { done?: boolean, body?: string } -> update a note
//   DELETE /notes/:id                                        -> soft-delete a note
//   GET    /categories                                       -> category/subcategory tree with open counts
//
// Parsing rule: first word of `text` = category, second word = subcategory,
// remaining words = the note body. Matching is case-insensitive; an unseen
// category or subcategory is created automatically. At least 3 words are
// required (category, subcategory, and at least one word of body) — this
// guards against a stray two-word note accidentally creating an empty item.

export interface Env {
  DB: D1Database;
  API_TOKEN: string;
}

const CORS_HEADERS = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Methods": "GET,POST,PATCH,DELETE,OPTIONS",
  "Access-Control-Allow-Headers": "Content-Type, Authorization",
};

function json(data: unknown, status = 200): Response {
  return new Response(JSON.stringify(data), {
    status,
    headers: { "Content-Type": "application/json", ...CORS_HEADERS },
  });
}

function error(message: string, status = 400): Response {
  return json({ error: message }, status);
}

function isAuthorized(request: Request, env: Env): boolean {
  const header = request.headers.get("Authorization") ?? "";
  const expected = `Bearer ${env.API_TOKEN}`;
  // Constant-time-ish comparison isn't critical here (single personal token,
  // low value target) but a plain === is fine for this use case.
  return header === expected;
}

interface ParsedNote {
  categoryWord: string;
  subcategoryWord: string;
  body: string;
}

function parseNoteText(raw: string): ParsedNote | null {
  const words = raw.trim().split(/\s+/).filter(Boolean);
  if (words.length < 3) return null;
  const [categoryWord, subcategoryWord, ...rest] = words;
  return { categoryWord, subcategoryWord, body: rest.join(" ") };
}

async function getOrCreateCategory(
  db: D1Database,
  name: string
): Promise<{ id: number; name: string; created: boolean }> {
  const key = name.toLowerCase();
  const existing = await db
    .prepare("SELECT id, name FROM categories WHERE name_key = ?")
    .bind(key)
    .first<{ id: number; name: string }>();
  if (existing) return { ...existing, created: false };

  const now = Date.now();
  const result = await db
    .prepare(
      "INSERT INTO categories (name, name_key, created_at) VALUES (?, ?, ?)"
    )
    .bind(name, key, now)
    .run();
  return { id: result.meta.last_row_id as number, name, created: true };
}

async function getOrCreateSubcategory(
  db: D1Database,
  categoryId: number,
  name: string
): Promise<{ id: number; name: string; created: boolean }> {
  const key = name.toLowerCase();
  const existing = await db
    .prepare(
      "SELECT id, name FROM subcategories WHERE category_id = ? AND name_key = ?"
    )
    .bind(categoryId, key)
    .first<{ id: number; name: string }>();
  if (existing) return { ...existing, created: false };

  const now = Date.now();
  const result = await db
    .prepare(
      "INSERT INTO subcategories (category_id, name, name_key, created_at) VALUES (?, ?, ?, ?)"
    )
    .bind(categoryId, name, key, now)
    .run();
  return { id: result.meta.last_row_id as number, name, created: true };
}

async function handleCreateNote(request: Request, env: Env): Promise<Response> {
  const payload = await request.json<{ text?: string }>().catch(() => null);
  if (!payload || typeof payload.text !== "string" || !payload.text.trim()) {
    return error("Body must be JSON: { \"text\": \"Category Subcategory your note\" }");
  }

  const parsed = parseNoteText(payload.text);
  if (!parsed) {
    return error(
      "Need at least 3 words: <category> <subcategory> <note text>. Got: \"" +
        payload.text +
        "\""
    );
  }

  const category = await getOrCreateCategory(env.DB, parsed.categoryWord);
  const subcategory = await getOrCreateSubcategory(
    env.DB,
    category.id,
    parsed.subcategoryWord
  );

  const now = Date.now();
  const result = await env.DB.prepare(
    "INSERT INTO notes (subcategory_id, body, done, created_at, updated_at, deleted) VALUES (?, ?, 0, ?, ?, 0)"
  )
    .bind(subcategory.id, parsed.body, now, now)
    .run();

  return json(
    {
      id: result.meta.last_row_id,
      category: category.name,
      categoryCreated: category.created,
      subcategory: subcategory.name,
      subcategoryCreated: subcategory.created,
      body: parsed.body,
      done: false,
      createdAt: now,
      updatedAt: now,
    },
    201
  );
}

async function handleListNotes(request: Request, env: Env): Promise<Response> {
  const url = new URL(request.url);
  const since = Number(url.searchParams.get("since") ?? 0) || 0;
  const includeDeleted = url.searchParams.get("includeDeleted") === "1";

  const query = `
    SELECT
      n.id, n.body, n.done, n.created_at as createdAt, n.updated_at as updatedAt, n.deleted,
      s.id as subcategoryId, s.name as subcategory,
      c.id as categoryId, c.name as category
    FROM notes n
    JOIN subcategories s ON s.id = n.subcategory_id
    JOIN categories c ON c.id = s.category_id
    WHERE n.updated_at >= ?
    ${includeDeleted ? "" : "AND n.deleted = 0"}
    ORDER BY n.updated_at DESC
    LIMIT 500
  `;
  const { results } = await env.DB.prepare(query).bind(since).all();
  return json({ notes: results });
}

async function handleUpdateNote(
  request: Request,
  env: Env,
  id: string
): Promise<Response> {
  const payload = await request
    .json<{ done?: boolean; body?: string }>()
    .catch(() => null);
  if (!payload || (payload.done === undefined && payload.body === undefined)) {
    return error('Body must include "done" and/or "body"');
  }

  const now = Date.now();
  if (payload.done !== undefined) {
    await env.DB.prepare(
      "UPDATE notes SET done = ?, updated_at = ? WHERE id = ?"
    )
      .bind(payload.done ? 1 : 0, now, id)
      .run();
  }
  if (payload.body !== undefined) {
    await env.DB.prepare(
      "UPDATE notes SET body = ?, updated_at = ? WHERE id = ?"
    )
      .bind(payload.body, now, id)
      .run();
  }
  return json({ id: Number(id), updatedAt: now });
}

async function handleDeleteNote(env: Env, id: string): Promise<Response> {
  const now = Date.now();
  await env.DB.prepare(
    "UPDATE notes SET deleted = 1, updated_at = ? WHERE id = ?"
  )
    .bind(now, id)
    .run();
  return json({ id: Number(id), deleted: true });
}

async function handleCategoryTree(env: Env): Promise<Response> {
  const { results } = await env.DB.prepare(
    `
    SELECT
      c.id as categoryId, c.name as category,
      s.id as subcategoryId, s.name as subcategory,
      COUNT(CASE WHEN n.done = 0 AND n.deleted = 0 THEN 1 END) as openCount,
      COUNT(CASE WHEN n.deleted = 0 THEN 1 END) as totalCount
    FROM categories c
    LEFT JOIN subcategories s ON s.category_id = c.id
    LEFT JOIN notes n ON n.subcategory_id = s.id
    GROUP BY s.id
    ORDER BY c.name, s.name
    `
  ).all();

  const tree = new Map<
    string,
    { category: string; categoryId: number; subcategories: unknown[] }
  >();
  for (const row of results as Record<string, unknown>[]) {
    const catKey = String(row.categoryId);
    if (!tree.has(catKey)) {
      tree.set(catKey, {
        category: row.category as string,
        categoryId: row.categoryId as number,
        subcategories: [],
      });
    }
    if (row.subcategoryId != null) {
      tree.get(catKey)!.subcategories.push({
        id: row.subcategoryId,
        name: row.subcategory,
        openCount: row.openCount,
        totalCount: row.totalCount,
      });
    }
  }
  return json({ categories: Array.from(tree.values()) });
}

export default {
  async fetch(request: Request, env: Env): Promise<Response> {
    if (request.method === "OPTIONS") {
      return new Response(null, { headers: CORS_HEADERS });
    }

    if (!isAuthorized(request, env)) {
      return error("Unauthorized", 401);
    }

    const url = new URL(request.url);
    const path = url.pathname.replace(/\/+$/, "") || "/";
    const noteIdMatch = path.match(/^\/notes\/(\d+)$/);

    try {
      if (path === "/notes" && request.method === "POST") {
        return await handleCreateNote(request, env);
      }
      if (path === "/notes" && request.method === "GET") {
        return await handleListNotes(request, env);
      }
      if (noteIdMatch && request.method === "PATCH") {
        return await handleUpdateNote(request, env, noteIdMatch[1]);
      }
      if (noteIdMatch && request.method === "DELETE") {
        return await handleDeleteNote(env, noteIdMatch[1]);
      }
      if (path === "/categories" && request.method === "GET") {
        return await handleCategoryTree(env);
      }
      return error("Not found", 404);
    } catch (err) {
      console.error(err);
      return error("Internal error: " + (err as Error).message, 500);
    }
  },
};
