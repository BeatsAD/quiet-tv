# Original cloud artwork

`cloud-source.png` is an original black-background cloud radiance reference created
with the built-in image generation tool. It contains no Apple Weather resources.
The original is kept intact; runtime data is encoded by
`python3 scripts/generate_weather_impostors.py` (offline NumPy and Pillow only).
Normal APK builds consume the checked-in resources and need neither authoring tool.

The encoder maps the black radiance reference to continuous cloud coverage,
coverage-weighted relief XY and diffuse visibility. Weighted channels avoid
transparent-border colors leaking into filtered mip levels. The four 512 by 256 banks
occupy separate texture-array layers, each with 16 texels of padding and a complete
mip chain through level 9. Filtering cannot cross into a neighbouring bank.
Each layer is flipped vertically for OpenGL.
The companion `cloud-blanket.rg8` is a 64 cubed, two-component density/ambient field
resampled from the original authored cumulus volume. Scattered clouds use the
photographic banks at four depths; blanket/rain skies retain the near density field.
Relief and density are artistic lighting proxies, not measured cloud geometry.
Packing, component counts and SHA-256 are in `weather-gpu/impostors.json`.
The original artwork is dedicated to CC0-1.0 to the extent possible under law;
see the application material license.

Generation mode: built-in image_gen, new image, opaque background.

Generation prompt:

> Use case photorealistic-natural. Technical original cloud density reference atlas, 2:1 landscape, FOUR quite different small cloud banks in a precise 2 by 2 grid on an exactly pure BLACK (#000000) background. Background MUST stay black everywhere outside clouds; no transparency. Each bank fits entirely within its own grid cell and occupies only 65 percent of that cell's width, leaving large black gutters and black margins. Top left: irregular thick cumulus group viewed gently from below, fragmented vapor tufts, asymmetric towering and flat regions. Top right: a different broken stratocumulus bank with small clouds of varied heights. Bottom left: broad layered stratus with a ragged grey underside. Bottom right: darker thick rain-cloud bank with irregular overlapping lobes. Appearance: actual photographic water-vapour scattering and density, detailed diffuse light inside the clouds, natural smooth tonal transitions, denoised clean photographic structure. Light comes diffusely from ABOVE and FRONT so the centers can be brighter than the edges; grey shaded lower parts. The black is a technical reference background, not a night scene. No bright rimlight, NO white contour or white glowing border, NO silver lining, no glow, no bloom, no film grain, stippling, dithering or texture noise. NOT cartoon, illustration, smooth balloon/pillow CGI, or four identical pancakes. Clouds must not touch or get cut by the frame or grid. No sun, ground, sky, stars, moon, text, labels, grid lines or watermarks.
