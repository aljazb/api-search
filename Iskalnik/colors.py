import colorsys


def get_different_colors(number):
    return ['#%02x%02x%02x' % tuple(int(x * 255) for x in colorsys.hsv_to_rgb(n / number, 1, 1)) for n in range(number)]
