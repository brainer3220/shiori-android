## API Reference

Programmatic access to your [Shiori](https://app.getrecall.ai/item/e5b87e4a-a398-4d97-886d-bb92f17847d3) links. Use the API to save, fetch, update, and delete links from your own scripts, apps, and integrations.

## Base URL

```
https://www.shiori.sh
```

## Authentication

All API requests require an API key sent as a Bearer token in the `Authorization` header.

### Getting an API key

Open **Settings** in [Shiori](https://app.getrecall.ai/item/e5b87e4a-a398-4d97-886d-bb92f17847d3), find the **API key** section, and click **Generate**. Copy the key immediately — it won't be shown again.

### Using the key

Include the key in every request:

```
curl https://www.shiori.sh/api/links \\
  -H "Authorization: Bearer shk_your_api_key_here"
```

## Rate Limiting

API requests are limited to **60 requests per minute** per API key. Link creation is further limited to 30 per minute. When rate limited, the API returns `429 Too Many Requests` with these headers:

| Header | Description |
| --- | --- |
| X-RateLimit-Limit | Maximum requests allowed |
| X-RateLimit-Remaining | Requests remaining in window |
| X-RateLimit-Reset | Unix timestamp when window resets |
| Retry-After | Seconds to wait before retrying |

## Response Format


All responses are JSON with a `success` boolean. On errors, an `error` string is included.

Success:

```
{
  "success": true,
  "links": [...],
  "total": 42
}
```

Error:

```
{
  "success": false,
  "error": "Invalid API key"
}
```

## Endpoints

### GET /api/links

Fetch a paginated list of your saved links.

#### Query parameters

| Parameter | Type | Default | Description |
| --- | --- | --- | --- |
| limit | integer | 25 | Number of links to return (max 100) |
| offset | integer | 0 | Number of links to skip |
| read | string | all | Filter by read status: `all`, `read`, or `unread` |
| sort | string | newest | Sort order: `newest` or `oldest` |
| tag | string | none | Filter links by tag ID or tag name |

```
curl "https://www.shiori.sh/api/links?limit=10&read=unread" \\
  -H "Authorization: Bearer shk_your_api_key_here"
```

Response:


```
{
  "success": true,
  "links": [
    {
      "id": "550e8400-e29b-41d4-a716-446655440000",
      "url": "https://example.com/article",
      "title": "Example Article",
      "domain": "example.com",
      "summary": "A brief AI-generated summary of the article.",
      "favicon_url": null,
      "image_url": null,
      "status": "created",
      "source": "api",
      "created_at": "2026-02-21T12:00:00.000Z",
      "updated_at": "2026-02-21T12:00:00.000Z",
      "read_at": null,
      "hn_url": null,
      "file_storage_path": null,
      "file_type": null,
      "file_mime_type": null,
      "notion_page_id": null
    }
  ],
  "total": 142
}
```

### POST /api/links

Save a new link. The link is created immediately and processed in the background (metadata extraction, AI summary).

#### Request body (JSON)

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| url | string | Yes | The URL to save |
| title | string | No | Custom title (auto-extracted if omitted) |
| read | boolean | No | Save as already read (default: false) |


```
curl -X POST https://www.shiori.sh/api/links \\
  -H "Authorization: Bearer shk_your_api_key_here" \\
  -H "Content-Type: application/json" \\
  -d '{"url": "https://example.com/article"}'
```

Response:

```
{
  "success": true,
  "linkId": "550e8400-e29b-41d4-a716-446655440000"
}
```

If the URL was already saved, the response includes `duplicate: true` and the existing link is bumped to the top of your inbox.

### PATCH /api/links

Mark one or more links as read or unread in a single request.

#### Request body (JSON)

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| ids | string[] | Yes | Array of link IDs to update |
| read | boolean | Yes | `true` to archive, `false` to move back to inbox |

```
curl -X PATCH https://www.shiori.sh/api/links \\
  -H "Authorization: Bearer shk_your_api_key_here" \\
  -H "Content-Type: application/json" \\
  -d '{"ids": ["550e8400-e29b-41d4-a716-446655440000"], "read": true}'
```

Response:

```
{
  "success": true,
  "updated": 1
}
```

### PATCH /api/links/:id

Update a single link. Supports two operations: toggling read status and editing the title/summary.

#### Toggle read status


| Field | Type | Required | Description |
| --- | --- | --- | --- |
| read | boolean | Yes | `true` to archive, `false` to move back to inbox |

```
curl -X PATCH https://www.shiori.sh/api/links/550e8400-e29b-41d4-a716-446655440000 \\
  -H "Authorization: Bearer shk_your_api_key_here" \\
  -H "Content-Type: application/json" \\
  -d '{"read": true}'
```

#### Edit title & summary

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| title | string | Yes | New title (1–500 characters) |
| summary | string or null | No | New summary (max 2000 characters), null to clear |

```
curl -X PATCH https://www.shiori.sh/api/links/550e8400-e29b-41d4-a716-446655440000 \\
  -H "Authorization: Bearer shk_your_api_key_here" \\
  -H "Content-Type: application/json" \\
  -d '{"title": "Updated Title", "summary": "New summary text"}'
```

Response:

```
{
  "success": true,
  "message": "Link updated",
  "linkId": "550e8400-e29b-41d4-a716-446655440000"
}
```

Returns `409` if the link is still being processed.

### DELETE /api/links/:id

Move a link to the trash. Trashed links are automatically and permanently deleted after 7 days.


```
curl -X DELETE https://www.shiori.sh/api/links/550e8400-e29b-41d4-a716-446655440000 \\
  -H "Authorization: Bearer shk_your_api_key_here"
```

Response:

```
{
  "success": true,
  "message": "Link deleted successfully",
  "linkId": "550e8400-e29b-41d4-a716-446655440000"
}
```

### PATCH /api/links/:id — Restore from trash

Restore a previously deleted link from the trash.

```
curl -X PATCH https://www.shiori.sh/api/links/550e8400-e29b-41d4-a716-446655440000 \\
  -H "Authorization: Bearer shk_your_api_key_here" \\
  -H "Content-Type: application/json" \\
  -d '{"restore": true}'
```

Response:

```
{
  "success": true,
  "message": "Link restored",
  "linkId": "550e8400-e29b-41d4-a716-446655440000"
}
```

### GET /api/links?trash=true

Fetch links that are in the trash. Supports the same `limit` and `offset` parameters as the regular list endpoint.

```
curl "https://www.shiori.sh/api/links?trash=true&limit=10" \\
  -H "Authorization: Bearer shk_your_api_key_here"
```

### DELETE /api/links

Permanently delete all links in the trash. This cannot be undone.


```
curl -X DELETE https://www.shiori.sh/api/links \\
  -H "Authorization: Bearer shk_your_api_key_here"
```

Response:

```
{
  "success": true,
  "deleted": 12
}
```

## Tags

Shiori exposes tags through dedicated endpoints and lets clients attach tags to a link.

### GET /api/tags

Fetch all tags for the authenticated account.

```
curl https://www.shiori.sh/api/tags \\
  -H "Authorization: Bearer shk_your_api_key_here"
```

Response:

```
{
  "success": true,
  "tags": [
    {
      "id": "tag-1",
      "name": "ai"
    }
  ]
}
```

### POST /api/tags

Create a tag.

```
curl -X POST https://www.shiori.sh/api/tags \\
  -H "Authorization: Bearer shk_your_api_key_here" \\
  -H "Content-Type: application/json" \\
  -d '{"name": "ai"}'
```

### PATCH /api/tags/:id

Rename a tag.

```
curl -X PATCH https://www.shiori.sh/api/tags/tag-1 \\
  -H "Authorization: Bearer shk_your_api_key_here" \\
  -H "Content-Type: application/json" \\
  -d '{"name": "llm"}'
```

### DELETE /api/tags/:id

Delete a tag.

```
curl -X DELETE https://www.shiori.sh/api/tags/tag-1 \\
  -H "Authorization: Bearer shk_your_api_key_here"
```

### PUT /api/links/:id/tags

Replace the tags attached to a link.

```
curl -X PUT https://www.shiori.sh/api/links/550e8400-e29b-41d4-a716-446655440000/tags \\
  -H "Authorization: Bearer shk_your_api_key_here" \\
  -H "Content-Type: application/json" \\
  -d '{"tagIds": ["tag-1"]}'
```

## Error Codes

| Status | Description |
| --- | --- |
| 400 | Bad request — missing or invalid parameters |
| 401 | Unauthorized — missing or invalid API key |
| 404 | Not found — link does not exist or not yours |
| 409 | Conflict — link is still being processed |
| 429 | Rate limited — too many requests |
| 500 | Server error |

