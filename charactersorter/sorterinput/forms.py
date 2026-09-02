from django import forms
from django.conf import settings

from .models import Character, CharacterList

def MaybeAppendShowImages(l):
    if settings.IMAGE_SEARCH_KEY == "":
        return l
    return l + ["show_images"]

ModifyCharFormset = forms.modelformset_factory(
    Character, fields=["name", "fandom"], can_delete=True,
    extra=0)

ModifyCharlistFormset = forms.modelformset_factory(
    CharacterList, fields=MaybeAppendShowImages(["title", "controller_type"]), can_delete=True,
    extra=0)

class AddCharForm(forms.ModelForm):
    """characterlist is set by the view from the URL, not submitted by the
    client, so it can't be pointed at someone else's list."""
    class Meta:
        model = Character
        fields = ["name", "fandom"]

class AddCharlistForm(forms.ModelForm):
    """owner is set by the view from request.user; same reasoning."""
    class Meta:
        model = CharacterList
        fields = MaybeAppendShowImages(["title", "controller_type"])
