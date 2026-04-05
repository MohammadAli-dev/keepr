Sprint 5: Intelligence Layer (OCR + Parsing + Confidence)
🎯 Sprint Goal

Build a robust, extensible intelligence pipeline that converts raw documents into structured, high-confidence entities (Device, Warranty, Invoice).

This sprint upgrades ingestion from:

File → Stub Data

to:

File → OCR → Parsed Data → Confidence Score → Structured Entities
🧠 Core Principles
Deterministic + AI Hybrid
Prefer deterministic parsing first
Use AI only as fallback
Confidence-Driven System
Every extracted field must have a confidence score
Non-Blocking Pipeline
No long AI/OCR calls inside DB transactions
Extensible Architecture
Easy to plug:
Google Vision
Tesseract
LLM providers
🏗️ Architecture Overview
RawDocument
   ↓
OCR Service
   ↓
Raw Text
   ↓
Parsing Engine
   ↓
Structured Data + Confidence
   ↓
Validation Layer
   ↓
Device / Warranty Creation
📦 Scope
🔴 1. OCR Layer (Real Implementation)
Objective

Replace stub OCR with real extraction capability.

Components
OcrService (Upgrade)
String extractText(String fileUrl)
Implementation Strategy
Phase 1 (Sprint 5):
Keep stub as default
Add pluggable interface
Add provider interface:
interface OcrProvider {
    String extractText(Path filePath);
}
Providers (structure only)
StubOcrProvider (default)
GoogleVisionOcrProvider (future)
TesseractOcrProvider (optional)
Rules
NO API calls inside transaction
OCR must run outside DB transaction
🔴 2. Parsing Engine (Core Intelligence)
Objective

Convert raw OCR text → structured fields

New Component
ParsingService
ExtractionResult parse(String rawText)
ExtractionResult (IMPORTANT)

Use Java record:

public record ExtractionResult(
    String productName,
    String brand,
    String model,
    LocalDate purchaseDate,
    LocalDate warrantyStart,
    LocalDate warrantyEnd,
    Double confidenceScore
) {}
Parsing Strategy (Tiered)
🟢 Tier 1: Deterministic Parsing
Regex-based extraction:
Dates
Warranty keywords
Brand detection
🟡 Tier 2: Heuristic Mapping
Common invoice formats
Keyword proximity:
"Model:", "Invoice Date", "IMEI"
🔴 Tier 3: AI Fallback (structure only in Sprint 5)
LLM parsing interface (no heavy integration yet)
interface AiParser {
    ExtractionResult parse(String rawText);
}
🔴 3. Confidence Scoring Engine
Objective

Every extraction must produce:

confidenceScore (0 → 1)
Rules

Example:

Field	Rule
productName	keyword match → +0.3
dates	regex match → +0.2
brand match	known brand → +0.2
structure consistency	+0.3
Implementation
double calculateConfidence(ExtractionResult result)
Thresholds
Score	Action
> 0.8	Auto-create
0.5–0.8	Create + mark "needs review" (future)
< 0.5	FAIL job
🔴 4. Validation Layer
Objective

Ensure extracted data is safe before persistence

Rules
purchaseDate ≤ today
warrantyEnd ≥ warrantyStart
productName not null
Output
Valid → continue
Invalid → FAIL job
🔴 5. Ingestion Pipeline Integration
Update flow

Inside processJob(jobId):

1. markProcessing()
2. OCR → rawText
3. parse → ExtractionResult
4. confidence scoring
5. validation
6. createDeviceIngestion()
7. createWarranty()
8. finalizeJob()
Failure Cases
Stage	Action
OCR fails	retry
Parsing fails	retry
Low confidence	FAILED
🔴 6. Database Changes
V16 Migration

Add to extraction_jobs:

ALTER TABLE extraction_jobs ADD COLUMN raw_text TEXT;
ALTER TABLE extraction_jobs ADD COLUMN confidence_score DOUBLE PRECISION;
🔴 7. Observability (IMPORTANT)
Logging

Log at each stage:

OCR completed
Parsing completed
Confidence score
Validation result
🔴 8. Testing
Integration Tests
New Tests
OCR → parsing → entity creation flow
Low confidence → job FAILED
Invalid date → validation failure
Partial extraction → still works
🚫 Explicit Non-Goals
No UI changes
No human review system (Sprint 6)
No real AI provider integration (only interface)
✅ Definition of Done
OCR pipeline working (stub + pluggable)
Parsing engine functional (regex + heuristics)
Confidence scoring implemented
Validation enforced
Jobs correctly succeed/fail
Integration tests passing