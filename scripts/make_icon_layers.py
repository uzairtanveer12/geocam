# Builds adaptive-icon layers from a single square icon (assets/icon-only.png).
# Foreground: the icon itself (the icon tool adds the safe-zone margin).
# Background: a solid colour sampled from the icon's top-left corner.
from PIL import Image
size = 1024
im = Image.open("assets/icon-only.png").convert("RGBA").resize((size, size), Image.LANCZOS)
im.save("assets/icon-foreground.png")
Image.new("RGBA", (size, size), im.getpixel((4, 4))).save("assets/icon-background.png")
print("icon layers created")
