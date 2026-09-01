from django.contrib.auth.models import User
from django.test import TestCase
from django.urls import reverse

import controller.models
from .models import Character, CharacterList

class ControllerTypeIntegrityTest(TestCase):
    def test_controller_type_integrity(self):
        for _, controller_type in CharacterList.CONTROLLER_CHOICES:
            self.assertIn(controller_type, controller.models.CONTROLLER_TYPES)



class PostBodyAuthorizationTest(TestCase):
    """Object IDs arriving in the POST body must be scoped to the requester,
    not trusted. requires_list_owner only validates the URL's list_id. Each
    test also exercises the honest path in the same request, so that
    over-restricting a queryset fails rather than passing quietly."""

    def setUp(self):
        self.victim = User.objects.create_user("victim", password="pw")
        self.attacker = User.objects.create_user("attacker", password="pw")
        self.their_list = CharacterList.objects.create(
            owner=self.victim, title="Theirs")
        self.their_char = Character.objects.create(
            characterlist=self.their_list, name="Alice", fandom="Wonderland")
        self.my_list = CharacterList.objects.create(
            owner=self.attacker, title="Mine")
        self.client.force_login(self.attacker)

    def test_editlist_ignores_foreign_character_ids(self):
        """A tampered form-N-id neither renames nor deletes a character in
        someone else's list, while the requester's own row still saves."""
        my_char = Character.objects.create(
            characterlist=self.my_list, name="Bob", fandom="Elsewhere")
        response = self.client.post(
            reverse("sorterinput:editlist", args=(self.my_list.id,)), {
                "form-TOTAL_FORMS": "2",
                "form-INITIAL_FORMS": "2",
                "form-MIN_NUM_FORMS": "0",
                "form-MAX_NUM_FORMS": "1000",
                "form-0-id": str(self.their_char.id),
                "form-0-name": "Pwned",
                "form-0-fandom": "Pwned",
                "form-0-DELETE": "on",
                "form-1-id": str(my_char.id),
                "form-1-name": "Renamed",
                "form-1-fandom": "Elsewhere",
            })
        self.assertEqual(response.status_code, 302)
        self.assertTrue(
            Character.objects.filter(pk=self.their_char.pk).exists())
        self.their_char.refresh_from_db()
        self.assertEqual(self.their_char.name, "Alice")
        my_char.refresh_from_db()
        self.assertEqual(my_char.name, "Renamed")

    def test_add_forms_ignore_client_supplied_ownership(self):
        """characterlist and owner come from the URL and request.user, so
        POSTing them has no effect on where the new object lands."""
        self.client.post(
            reverse("sorterinput:editlist", args=(self.my_list.id,)),
            {"characterlist": str(self.their_list.id),
             "name": "Mallory", "fandom": "Nowhere"})
        self.assertEqual(self.their_list.character_set.count(), 1)
        self.assertEqual(
            Character.objects.get(name="Mallory").characterlist, self.my_list)

        self.client.post(
            reverse("sorterinput:editcharlists"),
            {"owner": str(self.victim.id), "title": "Gift",
             "controller_type": CharacterList.INSERTION})
        self.assertEqual(
            CharacterList.objects.filter(owner=self.victim).count(), 1)
        self.assertEqual(
            CharacterList.objects.get(title="Gift").owner, self.attacker)

    def test_undo_rejects_sortrecord_from_another_list(self):
        their_record = self.make_record(self.their_list, "Wonderland")
        response = self.client.post(
            reverse("sorterinput:undo", args=(self.my_list.id,)),
            {"last": str(their_record.id)})
        self.assertEqual(response.status_code, 404)
        self.assertTrue(controller.models.SortRecord.objects.filter(
            pk=their_record.pk).exists())

        my_record = self.make_record(self.my_list, "Elsewhere")
        response = self.client.post(
            reverse("sorterinput:undo", args=(self.my_list.id,)),
            {"last": str(my_record.id)})
        self.assertEqual(response.status_code, 302)
        self.assertFalse(controller.models.SortRecord.objects.filter(
            pk=my_record.pk).exists())

    def make_record(self, charlist, fandom):
        chars = [Character.objects.create(
            characterlist=charlist, name=name, fandom=fandom)
                 for name in ("Carol", "Dave")]
        return controller.models.SortRecord.objects.create(
            charlist=charlist, char1=chars[0], char2=chars[1], value=1)
