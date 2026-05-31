=== Vulgar's OSC — Icon & Sounds Setup ===

--- MOD ICON ---
Place your icon at:
  src/main/resources/assets/stabshot/icon.png

Requirements:
  - Must be exactly 128x128 pixels (or 64x64 minimum)
  - PNG format only
  - This path is already set in fabric.mod.json as "icon": "assets/stabshot/icon.png"
  - After placing it, rebuild the mod — the icon shows in mod launchers (Modrinth App, Prism, etc.)

Tip: Use any image editor (Paint.NET, GIMP, Photoshop, etc.) to make a 128x128 PNG.
     A cannon or orbital strike symbol looks great.

--- CUSTOM SOUNDS ---
Place your explosion .ogg files at:
  src/main/resources/assets/stabshot/sounds/explosion1.ogg
  src/main/resources/assets/stabshot/sounds/explosion2.ogg

Requirements:
  - Must be .ogg format (use Audacity or online converters to convert from mp3/wav)
  - Mono or stereo both work; Minecraft resamples automatically
  - These are already registered in sounds.json

If you don't have custom sounds yet, the mod still plays 4 layered vanilla
explosion booms at high volume (SoundCategory.MASTER, volume 3.5–4.0),
so the cannon is always loud and audible regardless.

Sound roles:
  explosion1 = ground impact boom (plays at strike coordinates)
  explosion2 = incoming shriek/fire sound (plays slightly above strike)
