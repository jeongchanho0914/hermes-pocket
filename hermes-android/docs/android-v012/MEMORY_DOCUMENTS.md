# Native memory and Markdown documents

Reference: Hermes Agent revision `d795726f78e532ca31655f74656b4be63a907581`, `tools/memory_tool.py` (two targets and compact durable facts; reusable procedures belong in skills).

`MemoryDocuments` stores private `memories/USER.md`, `MEMORY.md` and user/agent-created relative Markdown documents. First initialization migrates the existing owner memory without overwriting existing files. MEMORY.md writes synchronize the legacy settings field. The two core documents are injected as context; additional documents load only on demand. Core files can be emptied but cannot be deleted. Ordered memory operation batches compute one final result and commit one atomic file replacement after approval and stale-state checks.

Native bridge contracts:

- `memoryDocumentsList` → `{documents:[{name,title,description,kind,chars,bytes,updatedAt,core}]}`.
- `memoryDocumentsRead {name}` → document metadata and full `content`.
- `memoryDocumentsSave {name,content}` → `{saved:true,name,chars,documents:[...]}`.
- `memoryDocumentsDelete {name}` → `{deleted:true,name,documents:[...]}`.

Agent tools: `memory` targets `user` or `memory`, defaults to `memory` for existing calls; supports read and add/replace/remove, or one ordered operations array. `memory_document` supports list/read/save/delete. Memory and skill writes retain the owner's actual native approval mode and active unlocked request gate; storing an instruction does not grant a tool permission. New credential-like secret literals are rejected. Skill packages, nested resources and the original bundled library are preserved.

Bounds: USER.md 1,375 characters; MEMORY.md 4,000 characters to retain the existing Android application's memory budget (upstream default is 2,200); other Markdown documents 100,000 characters, private package total 8 MiB and 256 files. Strict relative paths, UTF-8 and symlink rejection apply. This adapter's replace/remove use one unique literal substring; upstream's whole-entry replacement and new_text alias are not represented as identical behavior. No external memory-provider integration is claimed.

Automatic useful memory/skill maintenance is requested through the model's prompt and actual supplied tool schemas, with existing native approval rules. It is not a local arbitrary fact extractor or a guarantee that an unsupported model issues valid tool calls. Temporary status, credentials and speculative user traits must not be saved. Reusable skills should describe verified useful workflows, not be fabricated for every chat.

Host verification: `MemoryDocumentsHarness` uses actual temporary disk files; 21 checks cover migration, core listing, nested artifact creation, reopened context, on-demand extra documents, synchronization, stale approval, traversal, UTF-8 Markdown constraints, core deletion refusal, bounds, credential refusal, retained previous content, symlinks, ordered batch shape and unique literal replacement. No physical-device proof is claimed here.
