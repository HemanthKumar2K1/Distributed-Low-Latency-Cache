DROP TABLE IF EXISTS products CASCADE;
DROP TABLE IF EXISTS categories CASCADE;

CREATE TABLE categories (
  id   BIGSERIAL PRIMARY KEY,
  name VARCHAR(64) UNIQUE NOT NULL
);

CREATE TABLE products (
  id          BIGSERIAL PRIMARY KEY,
  sku         VARCHAR(80) UNIQUE NOT NULL,
  name        VARCHAR(255) NOT NULL,
  description TEXT,
  price       NUMERIC(12,2) NOT NULL,
  category_id BIGINT REFERENCES categories(id),
  is_active   BOOLEAN NOT NULL DEFAULT true,
  created_at  TIMESTAMP NOT NULL DEFAULT now(),
  updated_at  TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX idx_products_category ON products(category_id);

INSERT INTO categories (name) VALUES
  ('Accessory'),
  ('Air Conditioner'),
  ('Hardware'),
  ('Refrigerator'),
  ('Service'),
  ('Washing Machine'),
  ('Water Purifier');

CREATE TEMP TABLE seed (sid INT, name TEXT, code TEXT, cat TEXT, price NUMERIC, descr TEXT);
INSERT INTO seed VALUES
  (1, 'HDMI Cable 2 m', 'ACC-HDMI-2M', 'Accessory', 999.0, 'HDMI 2.1 cable, 2 metres, 48 Gbps.'),
  (2, 'CoolAir Breeze 1 Ton 3-Star Split AC', 'AC-CB-1T-3S', 'Air Conditioner', 28990.0, 'Split AC, 1 Ton cooling capacity, 3-Star energy rating. Colours available: White. Refrigerant: R32. Copper condenser.'),
  (3, 'CoolAir Breeze 1.5 Ton 5-Star Inverter AC', 'AC-CB-1.5T-5S', 'Air Conditioner', 41490.0, 'Split Inverter AC, 1.5 Ton, 5-Star rating. Colours available: White, Silver. Wi-Fi enabled. 4-way swing. Copper condenser. Refrigerant: R32.'),
  (4, 'CoolAir Breeze 2 Ton 3-Star Split AC', 'AC-CB-2T-3S', 'Air Conditioner', 45990.0, 'Split AC, 2 Ton cooling capacity, 3-Star energy rating. Colour: White. Suitable for rooms up to 250 sq ft.'),
  (5, 'CoolAir Breeze 1.5 Ton 3-Star Split AC', 'AC-CB-1.5T-3S', 'Air Conditioner', 33490.0, 'Split AC, 1.5 Ton, 3-Star rating. Colour: White. Anti-dust filter. Self-clean technology.'),
  (6, 'CoolAir Max 2 Ton Cassette AC', 'AC-CM-2T-CAS', 'Air Conditioner', 72500.0, 'Ceiling-mounted cassette AC, 2 Ton capacity. Colour: White. 4-way airflow. Commercial grade. Includes ceiling panel.'),
  (7, 'CoolAir Max 3 Ton Cassette AC', 'AC-CM-3T-CAS', 'Air Conditioner', 98500.0, 'Air Conditioner product. Standard specification.'),
  (8, 'CoolAir Max 5 Ton Floor-Standing AC', 'AC-CM-5T-FS', 'Air Conditioner', 145000.0, 'Commercial floor-standing AC, 5 Ton. Colour: White/Grey. Ideal for showrooms and large halls up to 600 sq ft.'),
  (9, 'CoolAir Window 1 Ton AC', 'AC-CW-1T', 'Air Conditioner', 22990.0, 'Window AC, 1 Ton, 3-Star. Colour: White. Compact design for small rooms.'),
  (10, 'CoolAir Window 1.5 Ton AC', 'AC-CW-1.5T', 'Air Conditioner', 27490.0, 'Window AC, 1.5 Ton, 3-Star. Colour: White. Dust filter. Auto-restart.'),
  (11, 'CoolAir Breeze 1 Ton 5-Star Inverter AC', 'AC-CB-1T-5S', 'Air Conditioner', 34990.0, 'Split Inverter AC, 1 Ton, 5-Star. Colours available: White, Champagne Gold. PM 2.5 filter. Wi-Fi ready.'),
  (12, 'CoolAir Portable 1 Ton AC', 'AC-CP-1T', 'Air Conditioner', 31500.0, 'Portable AC, 1 Ton. Colour: White. No installation required. Castor wheels. Exhaust hose included.'),
  (13, 'CoolAir Breeze 2 Ton 5-Star Inverter AC', 'AC-CB-2T-5S', 'Air Conditioner', 56990.0, 'Split Inverter AC, 2 Ton, 5-Star. Colours available: White, Midnight Blue. 6-in-1 convertible. Voice control compatible.'),
  (14, 'CoolAir Max 2.5 Ton Duct AC', 'AC-CM-2.5T-D', 'Air Conditioner', 89000.0, 'Concealed duct AC, 2.5 Ton. For false-ceiling installations. Commercial use.'),
  (15, 'CoolAir Max 1.5 Ton Tower AC', 'AC-CM-1.5T-TW', 'Air Conditioner', 52000.0, 'Tower/Floor-standing AC, 1.5 Ton. Colour: White. Bladeless air output. Premium design.'),
  (16, 'CoolAir AC Stabilizer 4 KVA', 'ACC-STAB-4K', 'Air Conditioner', 3500.0, 'Voltage stabilizer for ACs up to 2 Ton. Input range 150V-280V. Wall mountable.'),
  (17, 'FrostMax 260 L Double-Door Refrigerator', 'RF-FM-260DD', 'Refrigerator', 24990.0, 'Double-door, 260 litres. Colour: Silver. Frost-free. Convertible freezer. Inverter compressor. Energy rating: 3-Star.'),
  (18, 'FrostMax 340 L Double-Door Refrigerator', 'RF-FM-340DD', 'Refrigerator', 31490.0, 'Double-door, 340 litres. Colours available: Silver, Midnight Black. Frost-free. Inverter compressor. 4-Star energy rating.'),
  (19, 'FrostMax 190 L Single-Door Refrigerator', 'RF-FM-190SD', 'Refrigerator', 14990.0, 'Single-door, 190 litres. Colour: Grey. Direct-cool. Vegetable crisper. 2-Star.'),
  (20, 'FrostMax 580 L Side-by-Side Refrigerator', 'RF-FM-580SBS', 'Refrigerator', 62990.0, 'Side-by-side, 580 litres. Colour: Stainless Steel. Water dispenser. Ice maker. Inverter. 5-Star.'),
  (21, 'FrostLite 210 L Double-Door Refrigerator', 'RF-FL-210DD', 'Refrigerator', 18990.0, 'Double-door, 210 litres. Colour: White. Frost-free. Budget range.'),
  (22, 'FrostMax 420 L Triple-Door Refrigerator', 'RF-FM-420TD', 'Refrigerator', 45990.0, 'Triple-door, 420 litres. Colour: Dark Steel. Convertible zones. 5-Star inverter.'),
  (23, 'FrostMax 700 L French-Door Refrigerator', 'RF-FM-700FD', 'Refrigerator', 89990.0, 'French-door, 700 litres. Colour: Brushed Steel. Touch panel. Auto ice maker. Premium range.'),
  (24, 'FrostLite 95 L Mini Refrigerator', 'RF-FL-95MINI', 'Refrigerator', 8990.0, 'Mini fridge, 95 litres. Colour: Black. Single-door. Ideal for bedrooms/offices.'),
  (25, 'FrostMax 500 L Bottom-Mount Refrigerator', 'RF-FM-500BM', 'Refrigerator', 54990.0, 'Bottom-mount freezer, 500 litres. Colour: Silver. Inverter. Convertible modes.'),
  (26, 'FrostMax Deep Freezer 300 L', 'RF-FM-DF300', 'Refrigerator', 19990.0, 'Chest-type deep freezer, 300 litres. Colour: White. Commercial grade. Hard top.'),
  (27, 'WashPro 7 Kg Front-Load Washing Machine', 'WM-WP-7FL', 'Washing Machine', 29990.0, 'Front-load, 7 Kg capacity. Colour: Silver. Inverter motor. 15 wash programs. Steam wash.'),
  (28, 'WashPro 8 Kg Front-Load Washing Machine', 'WM-WP-8FL', 'Washing Machine', 35490.0, 'Front-load, 8 Kg capacity. Colour: Silver. Inverter motor. AI wash. Allergy care cycle.'),
  (29, 'WashPro 6.5 Kg Top-Load Washing Machine', 'WM-WP-6.5TL', 'Washing Machine', 16990.0, 'Top-load, 6.5 Kg. Colour: Grey. Fully automatic. 8 programs. Child lock.'),
  (30, 'WashPro 8 Kg Top-Load Washing Machine', 'WM-WP-8TL', 'Washing Machine', 21990.0, 'Top-load, 8 Kg. Colour: Dark Grey. Fully automatic. Aqua Surge pulsator. 10 programs.'),
  (31, 'CleanWave 7 Kg Semi-Automatic Washing Machine', 'WM-CW-7SA', 'Washing Machine', 11990.0, 'Semi-automatic, 7 Kg. Colour: Blue/White. Twin-tub. Buzzer. Lint collector.'),
  (32, 'CleanWave 8.5 Kg Semi-Automatic Washing Machine', 'WM-CW-8.5SA', 'Washing Machine', 13990.0, 'Semi-automatic, 8.5 Kg. Colour: Maroon/White. Twin-tub. Rust-proof body.'),
  (33, 'WashPro 10 Kg Front-Load Washer-Dryer', 'WM-WP-10WD', 'Washing Machine', 52990.0, 'Washer-dryer combo, 10 Kg wash / 7 Kg dry. Colour: Platinum Silver. Inverter. Steam refresh.'),
  (34, 'WashPro 9 Kg Top-Load Washing Machine', 'WM-WP-9TL', 'Washing Machine', 25990.0, 'Top-load, 9 Kg. Colour: Champagne Gold. Inverter motor. Soft-close lid. Stainless steel drum.'),
  (35, 'WashPro Portable 3 Kg Mini Washer', 'WM-WP-3MW', 'Washing Machine', 7990.0, 'Washing Machine product. Standard specification.'),
  (36, 'CleanWave 6 Kg Semi-Automatic Washing Machine', 'WM-CW-6SA', 'Washing Machine', 9490.0, 'Semi-automatic, 6 Kg. Colour: White/Blue. Budget range. Lint filter. 1400 RPM spin.'),
  (37, 'PureDrop UV Water Purifier', 'WP-PD-UV', 'Water Purifier', 7990.0, 'UV purification. Capacity: 7 litres. Colour: White/Blue. Suitable for municipal water. 6-stage filtration.'),
  (38, 'PureDrop RO+UV Water Purifier', 'WP-PD-ROUV', 'Water Purifier', 12990.0, 'RO+UV+UF purification. Capacity: 8 litres. Colour: White. TDS controller. Suitable for borewell water.'),
  (39, 'PureDrop RO+UV+Alkaline Water Purifier', 'WP-PD-ROUVA', 'Water Purifier', 16990.0, 'RO+UV+Alkaline. Capacity: 10 litres. Colour: White/Grey. Mineral boost technology. Smart indicators.'),
  (40, 'AquaGuard Gravity Water Purifier', 'WP-AG-GRAV', 'Water Purifier', 2490.0, 'Non-electric gravity-based purifier. Capacity: 16 litres. Colour: Blue/White. No installation needed.'),
  (41, 'PureDrop Tankless RO Water Purifier', 'WP-PD-TLRO', 'Water Purifier', 21990.0, 'Tankless RO. Real-time filtration. Colour: Matte Black. Touch panel. Hot & cold dispenser.'),
  (42, 'PureDrop Commercial RO System', 'WP-PD-COMRO', 'Water Purifier', 35000.0, 'Commercial RO, 50 LPH capacity. Stainless steel. For offices and restaurants.'),
  (43, 'PureDrop UV Filter Cartridge Pack (3-pack)', 'WP-PD-FC3', 'Water Purifier', 1490.0, 'Replacement filter cartridge pack for PureDrop UV models. 3 cartridges. 6-month life each.'),
  (44, 'PureDrop RO Membrane Replacement', 'WP-PD-ROM', 'Water Purifier', 2290.0, 'Replacement RO membrane for all PureDrop RO models. 12-month life.'),
  (45, 'Potter Annual Maintenance Contract - AC', 'SVC-AMC-AC', 'Service', 4999.0, 'Annual maintenance for any AC. Includes 2 services, gas top-up, and filter cleaning.'),
  (46, 'Potter Annual Maintenance Contract - Refrigerator', 'SVC-AMC-RF', 'Service', 2999.0, 'Annual maintenance for any refrigerator. Includes 2 services and thermostat check.'),
  (47, 'Potter Annual Maintenance Contract - Washing Machine', 'SVC-AMC-WM', 'Service', 3499.0, 'Annual maintenance for any washing machine. Includes 2 services, drum clean, and hose check.'),
  (48, 'Universal AC Remote Control', 'ACC-REMOTE-AC', 'Accessory', 599.0, 'Compatible with all CoolAir AC models. Backlit display. Timer function.'),
  (49, 'Washing Machine Stand with Wheels', 'ACC-WM-STAND', 'Accessory', 1299.0, 'Adjustable stand for front-load washing machines. 4 locking wheels. Anti-vibration pads.'),
  (50, 'Refrigerator Voltage Stabilizer 5 KVA', 'ACC-STAB-5K-RF', 'Accessory', 4200.0, 'Voltage stabilizer for refrigerators and freezers. Input range 140V-300V. LED display.'),
  (51, 'Water Purifier Installation Kit', 'ACC-WP-INSTKIT', 'Accessory', 499.0, 'Standard installation kit. Includes connectors, pipes, drill bits, and tap adaptor.'),
  (52, 'MSI Laptop Power Adapter', 'MSI-LAPTOP-POWER-ADAPTER', 'Hardware', 7000.0, '180W / 230W output, 19.5V DC, compatible with MSI gaming and workstation laptops'),
  (53, 'Laptop Backpack / Bag (MSI compatible)', 'LAPTOP-BACKPACK-BAG-MSI', 'Hardware', 4000.0, '15.6"–17.3" support, water-resistant fabric, padded laptop compartment'),
  (54, 'Cooling Pad', 'COOLING-PAD', 'Accessory', 2500.0, '2–5 cooling fans, USB powered, adjustable height, metal mesh surface'),
  (55, 'External Keyboard & Mouse', 'EXTERNAL-KEYBOARD-MOUSE', 'Hardware', 3000.0, 'USB / wireless, RGB (optional), gaming or productivity variants'),
  (56, 'USB Hub / Docking Station', 'USB-HUB-DOCKING-STATION', 'Hardware', 4500.0, 'USB-C, HDMI, Ethernet, USB 3.0 ports, 4K support (model dependent)');

INSERT INTO products (sku, name, description, price, category_id, is_active)
SELECT
  s.code || '-V' || g,
  s.name || ' - Model ' || (2020 + (g % 7)) || ' Rev ' || ((g % 13) + 1),
  s.descr
    || ' Model year ' || (2020 + (g % 7)) || '.'
    || ' Warranty: ' || ((g % 3) + 1) || ' years comprehensive, ' || ((g % 5) + 3) || ' years on compressor.'
    || ' Installation included within city limits. Energy label verified by BEE.'
    || ' Package contents: unit, remote, mounting kit, user manual, warranty card.'
    || ' Serviceable at ' || (120 + (g % 80)) || ' authorised centres nationwide.'
    || ' Dimensions (WxHxD): ' || (60 + (g % 40)) || ' x ' || (40 + (g % 30)) || ' x ' || (20 + (g % 25)) || ' cm.'
    || ' Net weight ' || (8 + (g % 45)) || ' kg. Batch reference ' || md5(g::text) || '.',
  round(s.price * (0.75 + (g % 100) / 200.0), 2),
  c.id,
  (g % 50) <> 0
FROM generate_series(1, 200000) g
JOIN seed s ON s.sid = (g % 56) + 1
JOIN categories c ON c.name = s.cat;

ANALYZE products;
ANALYZE categories;
