ALTER TABLE events
    ADD COLUMN global_theme JSONB DEFAULT NULL;

ALTER TABLE guests
    ADD COLUMN personal_theme JSONB DEFAULT NULL,
    ADD COLUMN allow_theme_switch BOOLEAN NOT NULL DEFAULT FALSE;

CREATE TABLE theme_presets (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    slug VARCHAR(120) NOT NULL UNIQUE,
    description VARCHAR(500),
    theme_data JSONB NOT NULL,
    is_builtin BOOLEAN NOT NULL DEFAULT TRUE,
    event_id BIGINT REFERENCES events(id) ON DELETE CASCADE,
    sort_order INT NOT NULL DEFAULT 0
);

CREATE INDEX idx_theme_presets_builtin_sort ON theme_presets(is_builtin, sort_order);
CREATE INDEX idx_theme_presets_event_sort ON theme_presets(event_id, sort_order);

INSERT INTO theme_presets (name, slug, description, theme_data, is_builtin, sort_order) VALUES
('Elegant Gold', 'elegant-gold', 'Warm cream background with gold accents and elegant serif headings.',
 $json$
{
  "colorBg": "#FBF7F0",
  "colorBgCard": "#FFFFFF",
  "colorText": "#2C2825",
  "colorTextSecondary": "#4A4541",
  "colorAccent": "#C9A84C",
  "colorAccentLight": "#E8D5A3",
  "colorAccentDark": "#A07D2E",
  "colorSuccess": "#7D9B76",
  "colorDanger": "#C07080",
  "fontDisplay": "Cormorant Garamond",
  "fontBody": "Outfit",
  "borderRadius": "16px",
  "cardShadow": "0 1px 3px rgba(0,0,0,0.04), 0 8px 32px rgba(0,0,0,0.04)",
  "backgroundPattern": "none",
  "backgroundGradient": "radial-gradient(ellipse at 20% 20%, rgba(201,168,76,0.06) 0%, transparent 50%)",
  "headerStyle": "centered",
  "animationStyle": "fadeUp"
 }$json$::jsonb, TRUE, 1),
('Neon Party', 'neon-party', 'Dark party theme with turquoise neon accents and glowing cards.',
 $json$
{
  "colorBg": "#0D0D0D",
  "colorBgCard": "#171717",
  "colorText": "#F7F7F7",
  "colorTextSecondary": "#C9C9C9",
  "colorAccent": "#00FFD1",
  "colorAccentLight": "#7DFFE9",
  "colorAccentDark": "#00B894",
  "colorSuccess": "#7CFF6B",
  "colorDanger": "#FF5C9A",
  "fontDisplay": "Orbitron",
  "fontBody": "Space Grotesk",
  "borderRadius": "14px",
  "cardShadow": "0 0 20px rgba(0,255,209,0.08)",
  "backgroundPattern": "grid",
  "backgroundGradient": "radial-gradient(circle at top right, rgba(0,255,209,0.16), transparent 36%)",
  "headerStyle": "centered",
  "animationStyle": "glow"
 }$json$::jsonb, TRUE, 2),
('Minimalist Mono', 'minimalist-mono', 'Black and white theme with thin borders and no shadows.',
 $json$
{
  "colorBg": "#FFFFFF",
  "colorBgCard": "#FFFFFF",
  "colorText": "#111111",
  "colorTextSecondary": "#5F5F5F",
  "colorAccent": "#111111",
  "colorAccentLight": "#E0E0E0",
  "colorAccentDark": "#000000",
  "colorSuccess": "#2F6F4E",
  "colorDanger": "#9D3045",
  "fontDisplay": "DM Serif Display",
  "fontBody": "DM Sans",
  "borderRadius": "2px",
  "cardShadow": "none",
  "backgroundPattern": "none",
  "backgroundGradient": "none",
  "headerStyle": "centered",
  "animationStyle": "fadeUp"
 }$json$::jsonb, TRUE, 3),
('Retro Vintage', 'retro-vintage', 'Paper-like beige texture with terracotta accents.',
 $json$
{
  "colorBg": "#F5E6CC",
  "colorBgCard": "#FFF8EA",
  "colorText": "#3F2E24",
  "colorTextSecondary": "#6D5546",
  "colorAccent": "#B85C3C",
  "colorAccentLight": "#E7B08D",
  "colorAccentDark": "#813D28",
  "colorSuccess": "#7D8F57",
  "colorDanger": "#A14E55",
  "fontDisplay": "Playfair Display",
  "fontBody": "Lora",
  "borderRadius": "12px",
  "cardShadow": "0 10px 28px rgba(86,54,30,0.12)",
  "backgroundPattern": "paper",
  "backgroundGradient": "none",
  "headerStyle": "centered",
  "animationStyle": "fadeUp"
 }$json$::jsonb, TRUE, 4),
('Sakura Soft', 'sakura-soft', 'Soft pink theme with rounded cards and gentle shadows.',
 $json$
{
  "colorBg": "#FFF5F7",
  "colorBgCard": "#FFFFFF",
  "colorText": "#3E2A33",
  "colorTextSecondary": "#7C5A68",
  "colorAccent": "#D4829C",
  "colorAccentLight": "#F3C3D0",
  "colorAccentDark": "#A85C75",
  "colorSuccess": "#78A97D",
  "colorDanger": "#C76582",
  "fontDisplay": "Zen Maru Gothic",
  "fontBody": "Quicksand",
  "borderRadius": "24px",
  "cardShadow": "0 12px 36px rgba(212,130,156,0.16)",
  "backgroundPattern": "dots",
  "backgroundGradient": "radial-gradient(circle at 20% 0%, rgba(212,130,156,0.14), transparent 34%)",
  "headerStyle": "centered",
  "animationStyle": "fadeUp"
 }$json$::jsonb, TRUE, 5);
