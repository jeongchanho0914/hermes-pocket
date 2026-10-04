# Original Hermes skill library

The app assets preserve all 210 `SKILL.md` packages in the audited Hermes Agent source commit `d795726f78e532ca31655f74656b4be63a907581`: 58 built-in packages and 152 optional packages. Original documents and 881 supporting files remain byte-for-byte unchanged, including nested resources and each package's license files. The manifest records SHA-256 hashes, source paths, original scope/category, file sizes, payload totals, and native import blockers.

These are instructions and source resources. Installing a package does not activate it, execute its scripts, install a runtime, grant tools or account access, or establish Android parity for its original prerequisites. Every package can be listed and its full original document read. 209 packages are eligible for native import under its resource size/path/file-count rules. The remaining package, `optional-skills/mlops/training/unsloth`, includes the unchanged 1,077,327-byte `references/llms-full.md`, which exceeds the 1 MiB resource limit. It remains visible and its skill document can be read. Installation also follows the user store capacity; catalog eligibility does not promise that all packages fit simultaneously.

`BuiltinSkillLibrary` reads `app/src/main/assets/hermes-skills/catalog.json`. `list()` returns package metadata, `summary()` returns source and counts, and `read(id)` verifies the archive/document hashes before returning original Markdown. `read(id, filePath)` reads a manifest-listed original resource with hash verification and a 1 MiB size bound, returning UTF-8 or base64. `install(id, store)` verifies the package and imports without replacement. Existing user packages survive repeated installation attempts. Library discovery and reading write nothing to user storage.

Reproduce assets offline from the audited source tree:

```sh
python3 scripts/package_hermes_skills.py
```

The packager validates inventory completeness and all audited `SKILL.md` hashes before packaging. ZIP timestamps/order/modes are deterministic. It never imports or runs original scripts. Symlinks, credential files, dependency trees, and compiled artifacts are not followed or packaged; any such exclusion is disclosed in the manifest and blocks complete native import. The current audited tree has no exclusions.

The source repository MIT license and attribution are preserved in `UPSTREAM-LICENSE.txt` and `UPSTREAM.txt`. Individual package license files and declarations are preserved unchanged; their own terms remain applicable.
