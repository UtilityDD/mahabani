# Bookshelf Assets - TODO

## Required Images

To complete the horizontal stacked bookshelf design, you need to add the following images to `app/src/main/res/drawable/`:

### Horizontal Spine Images
These should show the book lying flat with the spine facing the viewer (wide, short aspect ratio ~4:1 or 5:1):

- `spine_horizontal_kada_chabuk.png` - Brown leather with "KADA CHABUK" in gold
- `spine_horizontal_the_echo.png` - Red leather with "THE ECHO" in white
- `spine_horizontal_silent_hill.png` - Blue fabric with "SILENT HILL" in silver
- `spine_horizontal_lost_city.png` - Green leather with "LOST CITY" in gold
- `spine_horizontal_red_sky.png` - Burgundy leather with "RED SKY" in gold
- `spine_horizontal_ocean_deep.png` - Navy blue with "OCEAN DEEP" in silver

### Book Cover Images
These should show the front cover of each book (roughly square or portrait aspect ratio):

- `cover_kada_chabuk.png` - Brown leather front cover with ornate gold border and title
- `cover_the_echo.png` - Red leather cover
- `cover_silent_hill.png` - Blue fabric cover
- `cover_lost_city.png` - Green leather cover
- `cover_red_sky.png` - Burgundy leather cover
- `cover_ocean_deep.png` - Navy blue cover

## Current Status

**Placeholder Images**: The app is currently using the vertical spine images as placeholders. The functionality is fully implemented, but the visual appearance will be perfect once you add the proper horizontal spine and cover images.

## Updating the Code

Once you have the images, update `CoverActivity.kt` line ~147:

```kotlin
val allBooks = listOf(
    BookData("Kada Chabuk", R.drawable.spine_horizontal_kada_chabuk, R.drawable.cover_kada_chabuk),
    BookData("The Echo", R.drawable.spine_horizontal_the_echo, R.drawable.cover_the_echo),
    BookData("Silent Hill", R.drawable.spine_horizontal_silent_hill, R.drawable.cover_silent_hill),
    BookData("Lost City", R.drawable.spine_horizontal_lost_city, R.drawable.cover_lost_city),
    BookData("Red Sky", R.drawable.spine_horizontal_red_sky, R.drawable.cover_red_sky),
    BookData("Ocean Deep", R.drawable.spine_horizontal_ocean_deep, R.drawable.cover_ocean_deep)
)
```

## Adding More Books

To add a new book:
1. Create horizontal spine image: `spine_horizontal_[book_name].png`
2. Create cover image: `cover_[book_name].png`
3. Add to drawable folder
4. Add one line to the `allBooks` list:
   ```kotlin
   BookData("Book Title", R.drawable.spine_horizontal_book_name, R.drawable.cover_book_name)
   ```
